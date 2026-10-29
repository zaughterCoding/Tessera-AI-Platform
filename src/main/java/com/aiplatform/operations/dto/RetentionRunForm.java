package com.aiplatform.operations.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;

@Data
public class RetentionRunForm {
  @Min(1)
  @Max(100)
  private Integer maxBatches = 10;
}
