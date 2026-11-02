package com.aiplatform.generation.dto;

/** 管理可见的调度开关；密钥、回调地址与故障规则不在此暴露。 */
public record GenerationAdminSettingsDTO(
    boolean submitEnabled,
    boolean schedulingEnabled,
    boolean pollingEnabled,
    boolean simulatorEnabled,
    boolean simulatorFaultControlEnabled,
    boolean simulatorCallbackEnabled,
    String channelCode,
    int dispatchBatch,
    int pollBatch,
    int leaseSeconds,
    int submitMaxAttempts,
    int queryMaxAttempts,
    int userActiveLimit,
    int queueTimeoutSeconds,
    boolean fairSchedulingEnabled) {
}
