package com.aiplatform.generation.provider;

/** 渠道能力必须显式声明，缺少幂等或按尝试查询时不得进入自动恢复。 */
public record ProviderCapabilities(
    boolean idempotentSubmit,
    boolean queryByAttempt,
    boolean cancel) {
}
