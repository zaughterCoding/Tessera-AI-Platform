package com.aiplatform.generation.service;

import com.aiplatform.entitlement.service.IEntitlementSettlementService;
import com.aiplatform.generation.GenerationAccountingState;
import com.aiplatform.generation.GenerationCancelState;
import com.aiplatform.generation.GenerationResultState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.mapper.GenerationCancelMapper;
import com.aiplatform.generation.mapper.GenerationResultMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务最终结算的唯一编排入口：先用任务行条件更新抢占决定，再在同一事务内写入结算与流水。
 * 结果文件落盘和网络拉取都不在这里发生，调用方必须在完成可信事实后才进入本类。
 */
@Service
public class GenerationSettlementTransactions {
  private static final int NOTE_LIMIT = 255;
  private static final List<String> RELEASABLE_STATES = List.of(
      GenerationTaskState.QUEUED, GenerationTaskState.DISPATCHING,
      GenerationTaskState.SUBMITTED, GenerationTaskState.RUNNING,
      GenerationTaskState.RECONCILING, GenerationTaskState.REVIEW,
      GenerationTaskState.CANCEL_REQUESTED,
      GenerationTaskState.FAILED_PENDING_FOLLOWUP,
      GenerationTaskState.CANCELLED_PENDING_FOLLOWUP,
      GenerationTaskState.QUEUE_TIMEOUT,
      GenerationTaskState.SUCCEEDED, GenerationTaskState.FAILED,
      GenerationTaskState.CANCELLED);

  private final GenerationTaskMapper tasks;
  private final ProviderAttemptMapper attempts;
  private final GenerationResultMapper results;
  private final GenerationCancelMapper cancels;
  private final IEntitlementSettlementService settlements;

  public GenerationSettlementTransactions(GenerationTaskMapper tasks,
      ProviderAttemptMapper attempts, GenerationResultMapper results,
      GenerationCancelMapper cancels, IEntitlementSettlementService settlements) {
    this.tasks = tasks;
    this.attempts = attempts;
    this.results = results;
    this.cancels = cancels;
    this.settlements = settlements;
  }

  /** 成功交付：全部结果已保存且任务仍冻结时，消费一次并标记结果已交付。 */
  @Transactional
  public SettlementOutcome consume(long taskId) {
    GenerationTask task = tasks.selectById(taskId);
    if (task == null || !GenerationAccountingState.FROZEN.equals(task.getAccountingState())) {
      return SettlementOutcome.alreadySettled();
    }
    if (!deliverable(taskId, task)) {
      return SettlementOutcome.notApplicable();
    }
    int changed = tasks.finish(taskId, GenerationTaskState.SUCCEEDED,
        GenerationAccountingState.CONSUMED, "结果文件已保存并验证，任务完成",
        List.of(GenerationTaskState.RESULT_PENDING, GenerationTaskState.REVIEW));
    if (changed != 1) {
      return SettlementOutcome.alreadySettled();
    }
    IEntitlementSettlementService.SettlementResult result = settlements.consumeForTask(taskId,
        task.getGrantId(), task.getFrozenUnits());
    results.markDelivered(taskId);
    cancels.resolveOpen(taskId, GenerationCancelState.REJECTED, "UPSTREAM_SUCCEEDED");
    return new SettlementOutcome(true, result.ledgerId());
  }

  /** 确定失败或取消的唯一释放路径；调用方必须已经确认上游终态或确认从未提交。 */
  @Transactional
  public SettlementOutcome release(long taskId, String targetState, String note,
      List<String> expectedStates) {
    GenerationTask task = tasks.selectById(taskId);
    if (task == null || !GenerationAccountingState.FROZEN.equals(task.getAccountingState())) {
      return SettlementOutcome.alreadySettled();
    }
    int changed = tasks.finish(taskId, targetState, GenerationAccountingState.RELEASED,
        truncate(note), expectedStates);
    if (changed != 1) {
      return SettlementOutcome.notApplicable();
    }
    IEntitlementSettlementService.SettlementResult result = settlements.releaseForTask(taskId,
        task.getGrantId(), task.getFrozenUnits(), note);
    String outcome = switch (targetState) {
      case GenerationTaskState.CANCELLED -> "CANCELLED_TERMINAL";
      case GenerationTaskState.FAILED -> "UPSTREAM_FAILED";
      default -> "ALREADY_TERMINAL";
    };
    // 只有最终状态确实为取消时，用户的取消请求才算生效；上游失败或排队超时不得显示“取消已生效”。
    cancels.resolveOpen(taskId, GenerationTaskState.CANCELLED.equals(targetState)
        ? GenerationCancelState.RESOLVED : GenerationCancelState.REJECTED, outcome);
    return new SettlementOutcome(true, result.ledgerId());
  }

  /**
   * 恢复扫描：只按已有可信事实补结算。结果不完整时保持冻结，绝不猜测失败或退款。
   */
  @Transactional
  public SettlementOutcome recover(long taskId) {
    GenerationTask task = tasks.selectById(taskId);
    if (task == null || !GenerationAccountingState.FROZEN.equals(task.getAccountingState())) {
      return SettlementOutcome.alreadySettled();
    }
    var attempt = attempts.selectByTaskId(taskId);
    String attemptState = attempt == null ? null : attempt.getState();
    String state = task.getState();
    if (GenerationTaskState.RESULT_PENDING.equals(state)
        || GenerationTaskState.REVIEW.equals(state)) {
      if (deliverable(taskId, task)) {
        return consume(taskId);
      }
      return SettlementOutcome.notApplicable();
    }
    if ((GenerationTaskState.FAILED_PENDING_FOLLOWUP.equals(state)
        || GenerationTaskState.CANCEL_REQUESTED.equals(state))
        && ProviderAttemptState.FAILED.equals(attemptState)) {
      List<String> expected = GenerationTaskState.FAILED_PENDING_FOLLOWUP.equals(state)
          ? List.of(GenerationTaskState.FAILED_PENDING_FOLLOWUP)
          : List.of(GenerationTaskState.CANCEL_REQUESTED);
      return release(taskId, GenerationTaskState.FAILED,
          GenerationFactApplier.noteFor(attemptState), expected);
    }
    if ((GenerationTaskState.CANCELLED_PENDING_FOLLOWUP.equals(state)
        || GenerationTaskState.CANCEL_REQUESTED.equals(state))
        && ProviderAttemptState.CANCELLED.equals(attemptState)) {
      List<String> expected = GenerationTaskState.CANCELLED_PENDING_FOLLOWUP.equals(state)
          ? List.of(GenerationTaskState.CANCELLED_PENDING_FOLLOWUP)
          : List.of(GenerationTaskState.CANCEL_REQUESTED);
      return release(taskId, GenerationTaskState.CANCELLED,
          GenerationFactApplier.noteFor(attemptState), expected);
    }
    if (GenerationTaskState.QUEUE_TIMEOUT.equals(state)) {
      return release(taskId, GenerationTaskState.QUEUE_TIMEOUT,
          "排队超时，补记冻结释放", List.of(GenerationTaskState.QUEUE_TIMEOUT));
    }
    return SettlementOutcome.notApplicable();
  }

  /** 失败终态从任意未结算执行状态进入；排队取消等专用路径会传入更窄的状态集合。 */
  public static List<String> releasableStates() {
    return RELEASABLE_STATES;
  }

  private boolean deliverable(long taskId, GenerationTask task) {
    boolean stateAllowed = GenerationTaskState.RESULT_PENDING.equals(task.getState())
        || GenerationTaskState.REVIEW.equals(task.getState());
    if (!stateAllowed) {
      return false;
    }
    var attempt = attempts.selectByTaskId(taskId);
    if (attempt == null || !ProviderAttemptState.RESULT_PENDING.equals(attempt.getState())) {
      return false;
    }
    int outputCount = task.getOutputCount() == null ? 0 : task.getOutputCount();
    return outputCount > 0
        && results.countByTaskId(taskId) == outputCount
        && results.countByTaskState(taskId, GenerationResultState.SAVED) == outputCount;
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= NOTE_LIMIT ? value : value.substring(0, NOTE_LIMIT);
  }

  /** 结算结果用于调度器判断是否需要继续释放容量或退回用户活动名额。 */
  public record SettlementOutcome(boolean settled, long ledgerId) {
    public static SettlementOutcome alreadySettled() {
      return new SettlementOutcome(false, 0);
    }

    public static SettlementOutcome notApplicable() {
      return new SettlementOutcome(false, 0);
    }
  }
}
