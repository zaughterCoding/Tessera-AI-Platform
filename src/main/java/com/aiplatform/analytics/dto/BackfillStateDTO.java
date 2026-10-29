package com.aiplatform.analytics.dto;

import com.aiplatform.analytics.entity.AnalyticsBackfillState;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Data;

/** 补数状态视图；便于确认恢复范围与当前游标。 */
@Data
public class BackfillStateDTO {
  private String state;
  private Long cursorEventId;
  private String rangeFrom;
  private Long processed;
  private LocalDateTime startedAt;
  private LocalDateTime updatedAt;
  private String lastError;

  public static BackfillStateDTO from(AnalyticsBackfillState state) {
    BackfillStateDTO dto = new BackfillStateDTO();
    dto.setState(state.getState());
    dto.setCursorEventId(state.getCursorEventId());
    dto.setRangeFrom(Instant.ofEpochMilli(state.getRangeFromMs()).toString());
    dto.setProcessed(state.getProcessed());
    dto.setStartedAt(state.getStartedAt());
    dto.setUpdatedAt(state.getUpdatedAt());
    dto.setLastError(state.getLastError());
    return dto;
  }
}
