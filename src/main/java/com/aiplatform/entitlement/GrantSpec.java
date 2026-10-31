package com.aiplatform.entitlement;

/** 选包所需的固定规格；只用于筛选本人权益，不参与余额或执行参数的信任判断。 */
public record GrantSpec(
    long applicationId,
    int applicationVersion,
    String modelVersion,
    String imageSize,
    int outputCount) {
}
