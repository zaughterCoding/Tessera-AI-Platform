package com.aiplatform.generation.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 生成调度、模拟渠道与回调的独立开关；默认受控启用，故障控制默认关闭。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.generation")
public class GenerationProperties {
  private boolean submitEnabled;
  private boolean schedulingEnabled = true;
  private boolean pollingEnabled = true;
  private boolean simulatorEnabled = true;
  private boolean simulatorFaultControlEnabled;
  private boolean simulatorCallbackEnabled;
  private String simulatorCallbackUrl = "";
  private String channelCode = "simulated-primary";
  private int channelMaxInFlight = 4;
  private int channelQueueCapacity = 200;
  private int queueTimeoutSeconds = 1800;
  private int userActiveLimit = 5;
  private int defaultScheduleWeight = 1;
  private boolean fairSchedulingEnabled = true;
  private boolean adaptiveConcurrencyEnabled = true;
  private int adaptiveInitialInFlight = 4;
  private int adaptiveWindowSeconds = 5;
  private int adaptivePauseSeconds = 5;
  private int dispatchBatch = 20;
  private int pollBatch = 20;
  private int dispatchIntervalMs = 2000;
  private int pollScanIntervalMs = 2000;
  private int leaseSeconds = 30;
  private int submitMaxAttempts = 5;
  private int queryMaxAttempts = 8;
  private int retryBaseSeconds = 2;
  private int retryMaxSeconds = 60;
  private int pollIntervalSeconds = 2;
  private int reviewPollSeconds = 300;
  private int simulatorDefaultDurationMs = 3000;
  private int simulatorQueryFailures = 3;
  private int simulatorMaxConcurrent;
  private int simulatorCallbackTimeoutMs = 2000;
  private int callbackMaxSkewSeconds = 300;
  private int callbackMaxBytes = 8192;
  private String callbackSecret = "";

  @PostConstruct
  public void validate() {
    requireRange("channel-max-in-flight", channelMaxInFlight, 1, 1000);
    requireRange("channel-queue-capacity", channelQueueCapacity, 1, 100000);
    requireRange("queue-timeout-seconds", queueTimeoutSeconds, 1, 604800);
    requireRange("user-active-limit", userActiveLimit, 1, 10000);
    requireRange("default-schedule-weight", defaultScheduleWeight, 1, 10);
    requireRange("adaptive-initial-in-flight", adaptiveInitialInFlight, 1, 1000);
    requireRange("adaptive-window-seconds", adaptiveWindowSeconds, 1, 3600);
    requireRange("adaptive-pause-seconds", adaptivePauseSeconds, 1, 3600);
    requireRange("dispatch-batch", dispatchBatch, 1, 200);
    requireRange("poll-batch", pollBatch, 1, 200);
    requireRange("dispatch-interval-ms", dispatchIntervalMs, 200, 60000);
    requireRange("poll-scan-interval-ms", pollScanIntervalMs, 200, 60000);
    requireRange("lease-seconds", leaseSeconds, 5, 600);
    requireRange("submit-max-attempts", submitMaxAttempts, 1, 20);
    requireRange("query-max-attempts", queryMaxAttempts, 1, 50);
    requireRange("retry-base-seconds", retryBaseSeconds, 1, 300);
    requireRange("retry-max-seconds", retryMaxSeconds, retryBaseSeconds, 3600);
    requireRange("poll-interval-seconds", pollIntervalSeconds, 1, 300);
    requireRange("review-poll-seconds", reviewPollSeconds, 30, 86400);
    requireRange("simulator-default-duration-ms", simulatorDefaultDurationMs, 100, 600000);
    requireRange("simulator-query-failures", simulatorQueryFailures, 1, 20);
    requireRange("simulator-max-concurrent", simulatorMaxConcurrent, 0, 10000);
    requireRange("simulator-callback-timeout-ms", simulatorCallbackTimeoutMs, 100, 30000);
    requireRange("callback-max-skew-seconds", callbackMaxSkewSeconds, 0, 3600);
    requireRange("callback-max-bytes", callbackMaxBytes, 256, 65536);
    if (channelCode == null || !channelCode.matches("[a-z0-9-]{1,32}")) {
      throw new IllegalArgumentException("生成渠道编码不合法");
    }
    if (simulatorCallbackEnabled) {
      if (!simulatorCallbackUrl.matches("https?://[^\\s]+")) {
        throw new IllegalArgumentException("模拟回调地址必须是可信的 HTTP(S) 地址");
      }
      requireSecret();
    }
    if (!callbackSecret.isEmpty()) {
      requireSecret();
    }
  }

  private void requireSecret() {
    if (callbackSecret.length() < 32) {
      throw new IllegalArgumentException("生成回调密钥长度不足");
    }
  }

  private void requireRange(String name, long value, long min, long max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException("生成参数 " + name + " 超出允许范围");
    }
  }
}
