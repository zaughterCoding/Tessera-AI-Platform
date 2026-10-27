package com.aiplatform.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 发送与消费重试策略；默认值与既有可靠投递实现一致。 */
@Component
public class DeliveryRetryPolicy {
  private final int maxSendAttempts;
  private final int maxConsumeAttempts;
  private final long baseDelaySeconds;
  private final long maxDelaySeconds;

  public DeliveryRetryPolicy(
      @Value("${app.messaging.max-send-attempts:10}") int maxSendAttempts,
      @Value("${app.messaging.max-consume-attempts:5}") int maxConsumeAttempts,
      @Value("${app.messaging.retry-base-seconds:1}") long baseDelaySeconds,
      @Value("${app.messaging.retry-max-seconds:300}") long maxDelaySeconds) {
    this.maxSendAttempts = Math.max(1, maxSendAttempts);
    this.maxConsumeAttempts = Math.max(1, maxConsumeAttempts);
    this.baseDelaySeconds = Math.max(0, baseDelaySeconds);
    this.maxDelaySeconds = Math.max(1, maxDelaySeconds);
  }

  public int maxSendAttempts() {
    return maxSendAttempts;
  }

  public int maxConsumeAttempts() {
    return maxConsumeAttempts;
  }

  /** 指数退避，指数最多取 8，避免溢出并保留上限。 */
  public long delaySeconds(int attempts) {
    long factor = 1L << Math.min(Math.max(attempts, 0), 8);
    return Math.min(maxDelaySeconds, baseDelaySeconds * factor);
  }
}
