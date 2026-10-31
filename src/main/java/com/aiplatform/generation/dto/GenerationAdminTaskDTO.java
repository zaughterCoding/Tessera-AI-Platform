package com.aiplatform.generation.dto;

import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.GenerationHandoffMapper.HandoffRow;
import java.time.LocalDateTime;

/** 管理排障视图：任务、交接责任与稳定尝试的只读快照。 */
public record GenerationAdminTaskDTO(
    long id,
    long userId,
    long applicationId,
    String applicationName,
    String state,
    String stateNote,
    String accountingState,
    long grantId,
    int frozenUnits,
    LocalDateTime createdAt,
    String channelCode,
    String attemptState,
    String attemptKey,
    String providerTaskId,
    int attemptSubmitCount,
    int attemptQueryCount,
    String attemptFailureCode,
    String attemptFailureDetail,
    LocalDateTime attemptNextAt,
    String handoffState,
    long handoffGeneration,
    int handoffAttempts,
    LocalDateTime handoffNextAt,
    String handoffLastError) {

  public static GenerationAdminTaskDTO from(GenerationTask task, ProviderAttempt attempt,
      ProviderChannel channel, HandoffRow handoff) {
    return new GenerationAdminTaskDTO(task.getId(), task.getUserId(), task.getApplicationId(),
        task.getApplicationName(), task.getState(), task.getStateNote(), task.getAccountingState(),
        task.getGrantId(), task.getFrozenUnits(), task.getCreatedAt(),
        channel == null ? null : channel.getCode(),
        attempt == null ? null : attempt.getState(),
        attempt == null ? null : attempt.getAttemptKey(),
        attempt == null ? null : attempt.getProviderTaskId(),
        attempt == null ? 0 : value(attempt.getSubmitAttempts()),
        attempt == null ? 0 : value(attempt.getQueryAttempts()),
        attempt == null ? null : attempt.getFailureCode(),
        attempt == null ? null : attempt.getFailureDetail(),
        attempt == null ? null : attempt.getNextAttemptAt(),
        handoff == null ? null : handoff.getHandoffState(),
        handoff == null ? 0L : value(handoff.getGeneration()),
        handoff == null ? 0 : value(handoff.getAttempts()),
        handoff == null ? null : handoff.getNextAttemptAt(),
        handoff == null ? null : handoff.getLastError());
  }

  private static int value(Integer number) {
    return number == null ? 0 : number;
  }

  private static long value(Long number) {
    return number == null ? 0L : number;
  }
}
