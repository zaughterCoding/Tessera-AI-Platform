package com.aiplatform.generation.service;

/** 已认领的取消请求快照；请求状态在短事务内重新校验，渠道调用在事务外执行。 */
public record CancelClaim(
    long requestId,
    long taskId,
    long userId,
    String owner,
    long generation,
    int attempts,
    String taskState,
    String accountingState,
    long attemptId,
    long channelId,
    String attemptKey,
    String providerTaskId,
    String attemptState) {
}
