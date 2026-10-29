package com.aiplatform.operations.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RetentionStatusDTO {
  private String target;
  private String label;
  private String rule;
  private int retentionDays;
  private LocalDateTime cutoff;
  private long eligible;
  private int batchSize;
  private String managedBy;
}
