package com.aiplatform.operations.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RetentionRunDTO {
  private String target;
  private LocalDateTime cutoff;
  private int batches;
  private long deleted;
  private long remaining;
}
