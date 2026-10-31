package com.aiplatform.generation.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 模拟上游的独立持久状态；不参与业务事务，也没有业务表外键。 */
@Data
public class SimulatedProviderJob {
  private Long id;
  private String attemptKey;
  private String providerTaskId;
  private String state;
  private String faultMode;
  private Integer durationMs;
  private LocalDateTime completeAt;
  private Integer queryFailuresRemaining;
  private String resultRef;
  private String resultDigest;
  private String failureCode;
  private String failureDetail;
  private LocalDateTime createdAt;
  private LocalDateTime acceptedAt;
  private LocalDateTime terminalAt;
  private LocalDateTime updatedAt;
}
