package com.aiplatform.generation.service;

import com.aiplatform.generation.provider.ProviderSubmitCommand;

/** 已认领的上游尝试；owner 与 generation 是写回与接管的围栏。 */
public record AttemptClaim(
    long attemptId,
    long taskId,
    long userId,
    long applicationId,
    long channelId,
    String attemptKey,
    String providerTaskId,
    String state,
    String owner,
    long generation,
    long handoffId,
    long handoffGeneration,
    int submitAttempts,
    int queryAttempts,
    String modelVersion,
    String imageSize,
    int outputCount,
    String prompt,
    String negativePrompt,
    int mediaCount) {

  public ProviderSubmitCommand command() {
    return new ProviderSubmitCommand(attemptKey, taskId, userId, applicationId, modelVersion,
        imageSize, outputCount, prompt, negativePrompt, mediaCount);
  }
}
