package com.aiplatform.generation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 整体替换用户调度配置；字段为空表示恢复全局默认值。 */
@Data
public class GenerationUserSchedulingForm {
  @Min(1)
  @Max(200)
  private Integer activeLimit;

  @Min(1)
  @Max(10)
  private Integer scheduleWeight;
}
