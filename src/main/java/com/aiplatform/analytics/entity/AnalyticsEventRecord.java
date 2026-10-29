package com.aiplatform.analytics.entity;

import java.util.Objects;
import lombok.Data;

/** 已落库的去重明细；重复消息必须与已有内容完全一致。 */
@Data
public class AnalyticsEventRecord {
  private String eventId;
  private Long applicationId;
  private Long userId;
  private String action;
  private Long occurredAtMs;
  private Long bucketMs;

  public boolean matches(long expectedApplicationId, Long expectedUserId,
      String expectedAction, long expectedOccurredAtMs, long expectedBucketMs) {
    return Objects.equals(applicationId, expectedApplicationId)
        && Objects.equals(userId, expectedUserId)
        && Objects.equals(action, expectedAction)
        && Objects.equals(occurredAtMs, expectedOccurredAtMs)
        && Objects.equals(bucketMs, expectedBucketMs);
  }
}
