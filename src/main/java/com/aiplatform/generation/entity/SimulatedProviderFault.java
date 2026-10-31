package com.aiplatform.generation.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 一次性模拟故障规则；匹配后立即消费，不允许持久改变业务状态。 */
@Data
public class SimulatedProviderFault {
  private Long id;
  private String matchAttemptKey;
  private Long matchUserId;
  private Long matchApplicationId;
  private String mode;
  private Integer durationMs;
  private Integer remaining;
  private LocalDateTime createdAt;
}
