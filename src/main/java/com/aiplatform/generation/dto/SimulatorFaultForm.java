package com.aiplatform.generation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 仅在被显式开启的演示环境使用；规则只影响模拟渠道的匹配调用。 */
@Data
public class SimulatorFaultForm {
  @Pattern(regexp = "[0-9a-fA-F-]{36}")
  private String attemptKey;

  @Positive
  private Long userId;

  @Positive
  private Long applicationId;

  @Pattern(regexp = "REJECT_SUBMIT|LOSE_SUBMIT_RESPONSE|TIMEOUT_BUT_RUNNING|SLOW|FAIL"
      + "|QUERY_TEMPORARY_FAILURE")
  private String mode;

  @Min(100)
  @Max(600000)
  private Integer durationMs;

  @Min(1)
  @Max(10)
  private Integer remaining;
}
