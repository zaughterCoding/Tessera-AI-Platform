package com.aiplatform.generation.service;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.generation.GenerationAccountingState;
import com.aiplatform.generation.GenerationCancelState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.entity.GenerationCancelRequest;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.GenerationCancelMapper;
import com.aiplatform.generation.mapper.GenerationHandoffMapper;
import com.aiplatform.generation.mapper.GenerationQuotaMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 取消意图与排队取消的短事务；只有确认未提交的排队任务才会立即释放冻结。 */
@Service
public class GenerationCancelTransactions {
  private static final int REASON_LIMIT = 200;
  private static final String QUEUED_NOTE = "用户请求取消排队任务，等待确认未提交";

  private final GenerationTaskMapper tasks;
  private final ProviderAttemptMapper attempts;
  private final GenerationCancelMapper cancels;
  private final GenerationHandoffMapper handoffs;
  private final ProviderChannelMapper channels;
  private final GenerationQuotaMapper quotas;
  private final GenerationSettlementTransactions settlements;
  private final GenerationProperties properties;

  public GenerationCancelTransactions(GenerationTaskMapper tasks,
      ProviderAttemptMapper attempts, GenerationCancelMapper cancels,
      GenerationHandoffMapper handoffs, ProviderChannelMapper channels,
      GenerationQuotaMapper quotas, GenerationSettlementTransactions settlements,
      GenerationProperties properties) {
    this.tasks = tasks;
    this.attempts = attempts;
    this.cancels = cancels;
    this.handoffs = handoffs;
    this.channels = channels;
    this.quotas = quotas;
    this.settlements = settlements;
    this.properties = properties;
  }

  /**
   * 先锁任务行再判断排队状态，确保取消与调度认领对同一行串行；
   * 排队任务确认没有尝试后立即完成取消与冻结释放。
   */
  @Transactional
  public GenerationCancelRequest request(long userId, long taskId, String rawReason) {
    return requestInternal(userId, taskId, rawReason, true);
  }

  /** 管理代办取消同样走持久请求与同一状态机；权限由管理服务与审计入口保证。 */
  @Transactional
  public GenerationCancelRequest requestAsAdmin(long taskId, String rawReason) {
    return requestInternal(0L, taskId, rawReason, false);
  }

  private GenerationCancelRequest requestInternal(long userId, long taskId, String rawReason,
      boolean requireOwner) {
    GenerationTask task = tasks.selectByIdForUpdate(taskId);
    if (task == null || (requireOwner && !Objects.equals(task.getUserId(), userId))) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    cancels.insertIgnore(taskId, task.getUserId(), reason(rawReason));
    GenerationCancelRequest request = cancels.selectByTaskId(taskId);
    if (request == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    if (!GenerationCancelState.isOpen(request.getState())) {
      return request;
    }
    if (!GenerationAccountingState.FROZEN.equals(task.getAccountingState())
        || GenerationTaskState.isSettledFinal(task.getState())) {
      cancels.resolveOpen(taskId, GenerationCancelState.REJECTED, "ALREADY_TERMINAL");
      return cancels.selectByTaskId(taskId);
    }
    if (!cancellable(task.getState())) {
      cancels.resolveOpen(taskId, GenerationCancelState.REJECTED, "RESULT_ALREADY_FINAL");
      return cancels.selectByTaskId(taskId);
    }
    if (GenerationTaskState.QUEUED.equals(task.getState())
        && attempts.selectByTaskId(taskId) == null) {
      finishQueued(task, "USER_CANCELLED");
      return cancels.selectByTaskId(taskId);
    }
    // 已提交或认领中的任务只登记意图；渠道调用与最终决定交给后台工作者。
    tasks.markCancelRequested(taskId, "用户请求取消，等待上游确认");
    return cancels.selectByTaskId(taskId);
  }

  @Transactional
  public List<CancelClaim> claimDue(int limit) {
    List<GenerationCancelRequest> rows = cancels.selectDue(limit);
    List<CancelClaim> claims = new ArrayList<>();
    for (GenerationCancelRequest row : rows) {
      String owner = UUID.randomUUID().toString();
      if (cancels.claim(row.getId(), owner, properties.getLeaseSeconds()) != 1) {
        continue;
      }
      GenerationCancelRequest fresh = cancels.selectByTaskId(row.getTaskId());
      GenerationTask task = tasks.selectById(row.getTaskId());
      ProviderAttempt attempt = attempts.selectByTaskId(row.getTaskId());
      if (fresh == null || task == null) {
        continue;
      }
      claims.add(new CancelClaim(fresh.getId(), task.getId(), task.getUserId(), owner,
          fresh.getGeneration() == null ? 0 : fresh.getGeneration(),
          fresh.getAttempts() == null ? 1 : fresh.getAttempts(), task.getState(),
          task.getAccountingState(), attempt == null ? 0 : attempt.getId(),
          attempt == null ? 0 : attempt.getChannelId(),
          attempt == null ? null : attempt.getAttemptKey(),
          attempt == null ? null : attempt.getProviderTaskId(),
          attempt == null ? null : attempt.getState()));
    }
    return claims;
  }

  /** 确认未提交的排队取消：完成任务、释放队列与用户名额，并做唯一结算。 */
  @Transactional
  public boolean settleWithoutAttempt(CancelClaim claim) {
    GenerationTask task = tasks.selectByIdForUpdate(claim.taskId());
    if (task == null || !GenerationAccountingState.FROZEN.equals(task.getAccountingState())
        || attempts.selectByTaskId(claim.taskId()) != null) {
      return false;
    }
    if (!GenerationTaskState.QUEUED.equals(task.getState())
        && !GenerationTaskState.CANCEL_REQUESTED.equals(task.getState())) {
      return false;
    }
    if (GenerationTaskState.QUEUED.equals(task.getState())) {
      tasks.markCancelRequested(claim.taskId(), QUEUED_NOTE);
    }
    completeWaiting(claim.taskId(), "USER_CANCELLED");
    quotas.release(task.getUserId());
    settlements.release(claim.taskId(), GenerationTaskState.CANCELLED,
        "用户取消排队任务，已确认未提交上游", List.of(GenerationTaskState.CANCEL_REQUESTED));
    return true;
  }

  @Transactional
  public boolean finish(CancelClaim claim, String state, String outcome) {
    return cancels.finish(claim.requestId(), claim.owner(), claim.generation(), state,
        outcome) == 1;
  }

  @Transactional
  public boolean retry(CancelClaim claim, String error, int delaySeconds) {
    return cancels.markRetry(claim.requestId(), claim.owner(), claim.generation(),
        truncate(error), delaySeconds) == 1;
  }

  /** 渠道不支持或上游已终态时恢复执行状态，冻结与对账责任保持不变。 */
  @Transactional
  public boolean restore(CancelClaim claim, String requestState, String outcome) {
    GenerationTask task = tasks.selectByIdForUpdate(claim.taskId());
    ProviderAttempt attempt = attempts.selectByTaskId(claim.taskId());
    if (task != null && attempt != null
        && GenerationTaskState.CANCEL_REQUESTED.equals(task.getState())) {
      tasks.restoreFromCancel(claim.taskId(),
          ProviderAttemptState.taskState(attempt.getState()),
          "取消未生效，继续等待上游事实核对");
    }
    return cancels.finish(claim.requestId(), claim.owner(), claim.generation(), requestState,
        outcome) == 1;
  }

  private void finishQueued(GenerationTask task, String reason) {
    tasks.markCancelRequested(task.getId(), QUEUED_NOTE);
    completeWaiting(task.getId(), reason);
    quotas.release(task.getUserId());
    settlements.release(task.getId(), GenerationTaskState.CANCELLED,
        "用户取消排队任务，已确认未提交上游", List.of(GenerationTaskState.CANCEL_REQUESTED));
  }

  private void completeWaiting(long taskId, String reason) {
    ProviderChannel channel = channels.selectByCode(properties.getChannelCode());
    for (var row : handoffs.selectByTaskId(taskId)) {
      if ("PENDING".equals(row.getHandoffState())
          || "RETRY".equals(row.getHandoffState())) {
        if (handoffs.completeWaitingHandoff(row.getHandoffId(), row.getHandoffState(),
            reason) == 1 && channel != null) {
          channels.releaseQueue(channel.getId());
        }
      }
    }
  }

  private boolean cancellable(String state) {
    return !GenerationTaskState.RESULT_PENDING.equals(state)
        && !GenerationTaskState.FAILED_PENDING_FOLLOWUP.equals(state)
        && !GenerationTaskState.CANCELLED_PENDING_FOLLOWUP.equals(state)
        && !GenerationTaskState.QUEUE_TIMEOUT.equals(state);
  }

  private String reason(String value) {
    if (value == null) {
      return "用户请求取消";
    }
    String cleaned = value.replaceAll("\\p{Cntrl}", "").trim();
    if (cleaned.isEmpty()) {
      return "用户请求取消";
    }
    return cleaned.length() <= REASON_LIMIT ? cleaned : cleaned.substring(0, REASON_LIMIT);
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= 255 ? value : value.substring(0, 255);
  }
}
