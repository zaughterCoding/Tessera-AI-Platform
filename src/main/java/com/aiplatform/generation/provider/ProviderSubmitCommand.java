package com.aiplatform.generation.provider;

/** 提交给上游的最小事实快照；attemptKey 是不可更换的幂等身份。 */
public record ProviderSubmitCommand(
    String attemptKey,
    long taskId,
    long userId,
    long applicationId,
    String modelVersion,
    String imageSize,
    int outputCount,
    String prompt,
    String negativePrompt,
    int mediaCount) {
}
