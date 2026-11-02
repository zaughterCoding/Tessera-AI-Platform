package com.aiplatform.generation.service;

import com.aiplatform.generation.GenerationCancelState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.ProviderResultMetadata;
import com.aiplatform.generation.ResultManifest;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.mapper.GenerationCancelMapper;
import com.aiplatform.generation.mapper.GenerationHandoffMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import com.aiplatform.generation.provider.ProviderQueryResult;
import com.aiplatform.generation.provider.ProviderResultRef;
import com.aiplatform.generation.provider.ProviderSubmitResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 上游写回必须在短事务内校验 owner、generation 与状态迁移，绝不持锁等待网络。 */
@Service
public class GenerationUpstreamTransactions {
  private static final int NOTE_LIMIT = 255;
  private static final String THROTTLED = "UPSTREAM_THROTTLED";
  private static final String THROTTLED_DETAIL = "上游限流，提交未被接受";
  private static final String THROTTLED_NOTE = "上游限流，已重新排队；冻结次数保留";
  private final ProviderAttemptMapper attempts;
  private final GenerationHandoffMapper handoffs;
  private final GenerationTaskMapper tasks;
  private final GenerationFactApplier facts;
  private final GenerationResultTransactions resultTx;
  private final GenerationProperties properties;
  private final ProviderChannelMapper channels;
  private final GenerationCancelMapper cancels;

  public GenerationUpstreamTransactions(ProviderAttemptMapper attempts,
      GenerationHandoffMapper handoffs, GenerationTaskMapper tasks, GenerationFactApplier facts,
      GenerationResultTransactions resultTx, GenerationProperties properties,
      ProviderChannelMapper channels, GenerationCancelMapper cancels) {
    this.channels = channels;
    this.cancels = cancels;
    this.attempts = attempts;
    this.handoffs = handoffs;
    this.tasks = tasks;
    this.facts = facts;
    this.resultTx = resultTx;
    this.properties = properties;
  }

  @Transactional
  public void confirmSubmit(AttemptClaim claim, ProviderSubmitResult result) {
    if (result.immediate() != null) {
      // 同步渠道：受理与结果是同一个事实，同事务落库，不经过轮询。
      attempts.markSubmitted(claim.attemptId(), claim.owner(), claim.generation());
      terminal(claim, result.immediate());
      completeHandoff(claim, "ATTEMPT_ACCEPTED");
      return;
    }
    int changed = attempts.confirmSubmit(claim.attemptId(), claim.owner(), claim.generation(),
        result.providerTaskId(), result.state(), properties.getPollIntervalSeconds());
    if (changed == 1) {
      tasks.updateStateUnlessFinal(claim.taskId(), ProviderAttemptState.taskState(result.state()),
          GenerationFactApplier.noteFor(result.state()));
    }
    completeHandoff(claim, "ATTEMPT_ACCEPTED");
  }

  @Transactional
  public void markUncertain(AttemptClaim claim, String code, String detail) {
    int changed = attempts.markUncertain(claim.attemptId(), claim.owner(), claim.generation(),
        backoffSeconds(claim.submitAttempts() + 1), truncate(code), truncate(detail));
    if (changed == 1) {
      tasks.updateStateUnlessFinal(claim.taskId(), GenerationTaskState.RECONCILING,
          GenerationFactApplier.noteFor(ProviderAttemptState.RECONCILING));
    }
    completeHandoff(claim, "ATTEMPT_RECONCILING");
  }

  /**
   * 上游明确没有接受这次提交：归还在途预算并回到排队，冻结和用户名额保持不动。
   * 先锁任务行，和用户取消串行；取消已登记时直接按未提交取消结束，不再改回排队。
   */
  @Transactional
  public void requeueThrottled(AttemptClaim claim, Integer retryAfterSeconds) {
    GenerationTask task = tasks.selectByIdForUpdate(claim.taskId());
    String state = task == null ? null : task.getState();
    if (GenerationTaskState.CANCEL_REQUESTED.equals(state)) {
      if (attempts.applyTerminalWithLease(claim.attemptId(), claim.owner(), claim.generation(),
          ProviderAttemptState.CANCELLED, null, null, null, THROTTLED, THROTTLED_DETAIL) == 1) {
        facts.confirmedTerminal(claim.taskId(), claim.userId(), claim.channelId(),
            ProviderAttemptState.CANCELLED);
        cancels.resolveOpen(claim.taskId(), GenerationCancelState.RESOLVED, THROTTLED);
      }
      completeHandoff(claim, "ATTEMPT_CANCELLED");
      return;
    }
    if (!GenerationTaskState.DISPATCHING.equals(state)) {
      // 认领已过期并转入核对：保持原有的查询恢复流程。
      markUncertain(claim, THROTTLED, THROTTLED_DETAIL);
      return;
    }
    if (attempts.requeueThrottled(claim.attemptId(), claim.owner(), claim.generation(),
        THROTTLED, THROTTLED_DETAIL) != 1) {
      return;
    }
    if (tasks.updateStateIn(claim.taskId(), GenerationTaskState.QUEUED, THROTTLED_NOTE,
        List.of(GenerationTaskState.DISPATCHING)) != 1
        || channels.requeueInFlight(claim.channelId()) != 1) {
      throw new IllegalStateException("限流重排时任务或渠道计数不一致");
    }
    // 排队时限仍从最初入队算起：不改 enqueued_at，限流不会让任务永远排队。
    handoffs.markRetry(claim.handoffId(), "CLAIMED", throttleDelay(retryAfterSeconds), THROTTLED);
  }

  private int throttleDelay(Integer retryAfterSeconds) {
    if (retryAfterSeconds == null) {
      return properties.getRetryBaseSeconds();
    }
    return Math.max(1, Math.min(retryAfterSeconds, properties.getRetryMaxSeconds()));
  }

  @Transactional
  public void rejectSubmit(AttemptClaim claim, String code, String detail) {
    int changed = attempts.applyTerminalWithLease(claim.attemptId(), claim.owner(),
        claim.generation(), ProviderAttemptState.FAILED, null, null, null,
        truncate(code), truncate(detail));
    if (changed == 1) {
      facts.confirmedTerminal(claim.taskId(), claim.userId(), claim.channelId(),
          ProviderAttemptState.FAILED);
      // 用户能看到被拒绝的原因；原因来自适配器归一后的说明，不含上游完整响应。
      tasks.updateNote(claim.taskId(), truncate(detail
          + "；冻结次数已按权益包状态释放"));
    }
    completeHandoff(claim, "ATTEMPT_REJECTED");
  }

  @Transactional
  public void active(AttemptClaim claim, ProviderQueryResult result) {
    String state = result.status() == ProviderQueryResult.QueryStatus.ACCEPTED
        ? ProviderAttemptState.ACCEPTED : ProviderAttemptState.RUNNING;
    int changed = attempts.applyActive(claim.attemptId(), claim.owner(), claim.generation(),
        state, result.providerTaskId(), properties.getPollIntervalSeconds());
    if (changed == 1) {
      tasks.updateStateUnlessFinal(claim.taskId(), ProviderAttemptState.taskState(state),
          GenerationFactApplier.noteFor(state));
    }
  }

  @Transactional
  public void terminal(AttemptClaim claim, ProviderQueryResult result) {
    if (ProviderAttemptState.RESULT_PENDING.equals(claim.state())) {
      repairPendingResult(claim, result);
      return;
    }
    if (result.status() == ProviderQueryResult.QueryStatus.SUCCEEDED
        && !ProviderResultMetadata.isComplete(result.resultRef(), result.resultDigest())) {
      // 成功但结果元数据不完整：不能进入“元数据已记录”的终态，也不能释放在途预算。
      int changed = attempts.markReview(claim.attemptId(), claim.owner(), claim.generation(),
          "RESULT_METADATA_INCOMPLETE: 上游成功事件缺少结果引用或摘要",
          properties.getReviewPollSeconds());
      if (changed == 1) {
        tasks.updateStateUnlessFinal(claim.taskId(), GenerationTaskState.REVIEW,
            GenerationFactApplier.noteFor(ProviderAttemptState.REVIEW));
      }
      return;
    }
    String state = switch (result.status()) {
      case SUCCEEDED -> ProviderAttemptState.RESULT_PENDING;
      case FAILED -> ProviderAttemptState.FAILED;
      case CANCELLED -> ProviderAttemptState.CANCELLED;
      default -> throw new IllegalArgumentException("不是终态查询结果");
    };
    int changed = attempts.applyTerminalWithLease(claim.attemptId(), claim.owner(),
        claim.generation(), state, result.providerTaskId(), truncate(result.resultRef()),
        result.resultDigest(), truncate(result.failureCode()), truncate(result.failureDetail()));
    if (changed == 1) {
      if (ProviderAttemptState.RESULT_PENDING.equals(state)) {
        seedResults(claim.taskId(), claim.attemptId(), claim.outputCount(), result.results(),
            result.resultRef(), result.resultDigest());
      }
      facts.confirmedTerminal(claim.taskId(), claim.userId(), claim.channelId(), state);
    }
  }

  /** 终态已成立时只允许补齐缺失元数据；任何迟到或退化事实都不能反转状态或重复释放。 */
  private void repairPendingResult(AttemptClaim claim, ProviderQueryResult result) {
    if (result.status() == ProviderQueryResult.QueryStatus.SUCCEEDED) {
      int changed = 0;
      if (ProviderResultMetadata.isComplete(result.resultRef(), result.resultDigest())) {
        changed = attempts.completePendingResultWithLease(claim.attemptId(), claim.owner(),
            claim.generation(), result.providerTaskId(), truncate(result.resultRef()),
            result.resultDigest());
      }
      seedResults(claim.taskId(), claim.attemptId(), claim.outputCount(), result.results(),
          result.resultRef(), result.resultDigest());
      if (changed == 1 || resultTx.isComplete(claim.taskId(), claim.outputCount())) {
        return;
      }
    }
    attempts.delayPendingResultRepair(claim.attemptId(), claim.owner(), claim.generation(),
        properties.getReviewPollSeconds(), "RESULT_METADATA_PENDING: 上游结果元数据尚未补齐");
  }

  /** 只有清单完整时才登记结果；缺失时不释放容量、不生成占位文件，等待补查。 */
  private void seedResults(long taskId, long attemptId, int outputCount,
      List<ProviderResultRef> results, String ref, String digest) {
    List<ProviderResultRef> manifest = ResultManifest.complete(results, outputCount);
    if (manifest == null) {
      manifest = ResultManifest.legacy(ref, digest, outputCount);
    }
    if (manifest != null) {
      resultTx.seed(taskId, attemptId, outputCount, manifest);
    }
  }

  /** 取消确认与容量、冻结释放同事务；失败时不改变任何既有事实。 */
  @Transactional
  public boolean confirmCancel(long taskId, long userId, long channelId, long attemptId) {
    if (attempts.confirmUpstreamCancelled(attemptId) != 1) {
      return false;
    }
    facts.confirmedTerminal(taskId, userId, channelId, ProviderAttemptState.CANCELLED);
    return true;
  }

  @Transactional
  public void delayResultRepair(AttemptClaim claim, String code, String detail) {
    attempts.delayPendingResultRepair(claim.attemptId(), claim.owner(), claim.generation(),
        properties.getReviewPollSeconds(), truncate(code + ": " + detail));
  }

  @Transactional
  public void resubmit(AttemptClaim claim, ProviderSubmitResult result) {
    int changed = attempts.recordResubmit(claim.attemptId(), claim.owner(), claim.generation(),
        result.providerTaskId(), result.state(), properties.getPollIntervalSeconds());
    if (changed == 1) {
      tasks.updateStateUnlessFinal(claim.taskId(), ProviderAttemptState.taskState(result.state()),
          GenerationFactApplier.noteFor(result.state()));
    }
  }

  @Transactional
  public void queryFailure(AttemptClaim claim, String code, String detail, boolean exhausted) {
    int delay = backoffSeconds(claim.queryAttempts() + 1);
    int changed = attempts.recordQueryFailure(claim.attemptId(), claim.owner(), claim.generation(),
        exhausted, delay, truncate(code + ": " + detail));
    if (changed == 1) {
      String taskState = exhausted ? GenerationTaskState.REVIEW
          : GenerationTaskState.RECONCILING;
      String attemptState = exhausted ? ProviderAttemptState.REVIEW
          : ProviderAttemptState.RECONCILING;
      tasks.updateStateUnlessFinal(claim.taskId(), taskState,
          GenerationFactApplier.noteFor(attemptState));
    }
  }

  @Transactional
  public void review(AttemptClaim claim, String code, String detail) {
    int changed = attempts.markReview(claim.attemptId(), claim.owner(), claim.generation(),
        truncate(code + ": " + detail), properties.getReviewPollSeconds());
    if (changed == 1) {
      tasks.updateStateUnlessFinal(claim.taskId(), GenerationTaskState.REVIEW,
          GenerationFactApplier.noteFor(ProviderAttemptState.REVIEW));
    }
  }

  private void completeHandoff(AttemptClaim claim, String reason) {
    if (claim.handoffId() > 0) {
      handoffs.completeClaimedHandoff(claim.handoffId(), claim.handoffGeneration(), reason);
    }
  }

  private int backoffSeconds(int attempts) {
    int exponent = Math.min(Math.max(attempts - 1, 0), 10);
    long value = (long) properties.getRetryBaseSeconds() << exponent;
    return (int) Math.min(value, properties.getRetryMaxSeconds());
  }

  private String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= NOTE_LIMIT ? value : value.substring(0, NOTE_LIMIT);
  }
}
