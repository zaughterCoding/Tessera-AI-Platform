package com.aiplatform.analytics;

import javax.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 分析链路的独立开关与有界参数；关闭时不影响目录与交易。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsProperties {
  private boolean enabled;
  private String topic;
  private String group;
  private String bootstrapServers;
  private String clientId = "ai-creation-analytics";
  private int visitQueueCapacity = 512;
  private int visitAttempts = 3;
  private long sendTimeoutMs = 3000;
  private int relayBatch = 50;
  private long leaseSeconds = 30;
  private boolean relayEnabled = true;
  private int maxSendAttempts = 30;
  private long maxRetrySeconds = 300;
  private long pollDelayMs = 1000;
  private boolean consumerEnabled = true;
  private int pollRecords = 50;
  private long pollTimeoutMs = 1000;
  private long retryBackoffMs = 1000;
  private boolean backfillEnabled = true;
  private int backfillDays = 7;
  private int backfillBatch = 200;
  private long backfillIntervalMs = 500;
  private int retentionDays = 30;
  private int maxClockSkewMinutes = 5;
  private long cleanupIntervalMs = 3600000;
  private int heatVisitWeight = 1;
  private int heatUvWeight = 2;
  private int heatOrderWeight = 5;
  private int heatPaymentWeight = 10;
  private int heatClaimWeight = 8;

  @PostConstruct
  public void validate() {
    if (!enabled) {
      return;
    }
    requireTopic("topic", topic);
    requireTopic("group", group);
    if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
      throw new IllegalArgumentException("分析开启时必须配置 Kafka 地址");
    }
    requireRange("visit-queue-capacity", visitQueueCapacity, 1, 100000);
    requireRange("visit-attempts", visitAttempts, 1, 10);
    requireRange("send-timeout-ms", sendTimeoutMs, 100, 60000);
    requireRange("relay-batch", relayBatch, 1, 500);
    requireRange("lease-seconds", leaseSeconds, 1, 600);
    requireRange("max-send-attempts", maxSendAttempts, 1, 1000);
    requireRange("max-retry-seconds", maxRetrySeconds, 1, 3600);
    requireRange("poll-delay-ms", pollDelayMs, 10, 60000);
    requireRange("poll-records", pollRecords, 1, 500);
    requireRange("poll-timeout-ms", pollTimeoutMs, 10, 60000);
    requireRange("retry-backoff-ms", retryBackoffMs, 10, 60000);
    requireRange("backfill-days", backfillDays, 1, 365);
    requireRange("backfill-batch", backfillBatch, 1, 1000);
    requireRange("backfill-interval-ms", backfillIntervalMs, 10, 60000);
    requireRange("retention-days", retentionDays, 1, 365);
    requireRange("max-clock-skew-minutes", maxClockSkewMinutes, 0, 60);
    requireRange("cleanup-interval-ms", cleanupIntervalMs, 1000, 604800000);
    if (backfillDays > retentionDays) {
      throw new IllegalArgumentException("补数范围不能超过事件保留期");
    }
    requireWeight("heat-visit-weight", heatVisitWeight);
    requireWeight("heat-uv-weight", heatUvWeight);
    requireWeight("heat-order-weight", heatOrderWeight);
    requireWeight("heat-payment-weight", heatPaymentWeight);
    requireWeight("heat-claim-weight", heatClaimWeight);
  }

  public long retentionMs() {
    return retentionDays * 86400000L;
  }

  public long maxSkewMs() {
    return maxClockSkewMinutes * 60000L;
  }

  public long backfillRangeMs() {
    return backfillDays * 86400000L;
  }

  private void requireTopic(String name, String value) {
    if (value == null || !value.matches("[a-zA-Z0-9._-]{1,249}")) {
      throw new IllegalArgumentException("分析参数 " + name + " 不合法");
    }
  }

  private void requireRange(String name, long value, long min, long max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException("分析参数 " + name + " 超出允许范围");
    }
  }

  private void requireWeight(String name, int value) {
    requireRange(name, value, 0, 1000);
  }
}
