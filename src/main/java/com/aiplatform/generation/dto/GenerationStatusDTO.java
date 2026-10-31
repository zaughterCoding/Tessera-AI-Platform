package com.aiplatform.generation.dto;

/** 公开可见的提交与调度开关；不暴露内部密钥、额度或故障控制。 */
public record GenerationStatusDTO(
    boolean submitEnabled,
    boolean schedulingEnabled,
    boolean executionStarted,
    boolean simulatedChannel,
    String channelCode,
    String channelDisplayName,
    String notice) {
}
