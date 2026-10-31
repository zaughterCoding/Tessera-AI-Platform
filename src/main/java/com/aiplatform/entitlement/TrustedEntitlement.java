package com.aiplatform.entitlement;

import java.time.LocalDateTime;

/** 从订单与支付事实重新核对后的固定发放依据；消息载荷不参与任何字段。 */
public record TrustedEntitlement(
    long orderId,
    long userId,
    long productId,
    long applicationId,
    String applicationName,
    int applicationVersion,
    String modelVersion,
    String imageSize,
    int outputCount,
    int credits,
    int validDays,
    String funding,
    LocalDateTime paidAt,
    LocalDateTime expiresAt) {}
