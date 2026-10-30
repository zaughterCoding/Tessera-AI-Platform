package com.aiplatform.operations.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RetentionRunForm {
  @Min(1)
  @Max(100)
  private Integer maxBatches = 10;
}
