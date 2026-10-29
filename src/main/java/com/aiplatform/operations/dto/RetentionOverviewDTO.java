package com.aiplatform.operations.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RetentionOverviewDTO {
  private List<RetentionStatusDTO> database = new ArrayList<>();
  private MediaRetentionDTO media;
  private int analyticsRetentionDays;
  private int analyticsBackfillDays;
  private String analyticsNote;
}
