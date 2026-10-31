package com.aiplatform.generation.service;

import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.ProviderResultMetadata;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.mapper.GenerationHandoffMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.provider.ProviderQueryResult;
import com.aiplatform.generation.provider.ProviderSubmitResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 上游写回必须在短事务内校验 owner、generation 与状态迁移，绝不持锁等待网络。 */
@Service
public class GenerationUpstreamTransactions {
  private static final int NOTE_LIMIT = 255;
  private final ProviderAttemptMapper attempts;
  private final GenerationHandoffMapper handoffs;
  private final GenerationTaskMapper tasks;
  private final GenerationFactApplier facts;
  private final GenerationProperties properties;

  public GenerationUpstreamTransactions(ProviderAttemptMapper attempts,
      GenerationHandoffMapper handoffs, GenerationTaskMapper tasks, GenerationFactApplier facts,
      GenerationProperties properties) {
    this.attempts = attempts;
    this.handoffs = handoffs;
    this.tasks = tasks;
    this.facts = facts;
    this.properties = properties;
  }

  @Transactional
  public void confirmSubmit(AttemptClaim claim, ProviderSubmitResult result) {
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

  @Transactional
  public void rejectSubmit(AttemptClaim claim, String code, String detail) {
    int changed = attempts.applyTerminalWithLease(claim.attemptId(), claim.owner(),
        claim.generation(), ProviderAttemptState.FAILED, null, null, null,
        truncate(code), truncate(detail));
    if (changed == 1) {
      facts.confirmedTerminal(claim.taskId(), claim.userId(), claim.channelId(),
          ProviderAttemptState.FAILED);
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
      facts.confirmedTerminal(claim.taskId(), claim.userId(), claim.channelId(), state);
    }
  }

  /** 终态已成立时只允许补齐缺失元数据；任何迟到或退化事实都不能反转状态或重复释放。 */
  private void repairPendingResult(AttemptClaim claim, ProviderQueryResult result) {
    if (result.status() == ProviderQueryResult.QueryStatus.SUCCEEDED
        && ProviderResultMetadata.isComplete(result.resultRef(), result.resultDigest())) {
      int changed = attempts.completePendingResultWithLease(claim.attemptId(), claim.owner(),
          claim.generation(), result.providerTaskId(), truncate(result.resultRef()),
          result.resultDigest());
      if (changed == 1) {
        return;
      }
    }
    attempts.delayPendingResultRepair(claim.attemptId(), claim.owner(), claim.generation(),
        properties.getReviewPollSeconds(), "RESULT_METADATA_PENDING: 上游结果元数据尚未补齐");
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
