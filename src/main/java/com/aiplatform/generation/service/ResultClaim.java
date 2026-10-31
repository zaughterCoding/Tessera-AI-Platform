package com.aiplatform.generation.service;

/** 结果工作者在短事务中认领到的不可变快照；owner 与 generation 是落库围栏。 */
public record ResultClaim(
    long resultId,
    long taskId,
    long attemptId,
    long userId,
    int position,
    String sourceRef,
    String expectedDigest,
    String owner,
    long generation,
    int saveAttempts,
    int outputCount) {
}
