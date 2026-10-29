package com.aiplatform.analytics.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 历史补数游标；每批与游标推进同事务提交，重启后从原游标继续。 */
@Data
public class AnalyticsBackfillState {
  private Integer id;
  private String state;
  private Long cursorEventId;
  private Long rangeFromMs;
  private Long processed;
  private LocalDateTime startedAt;
  private LocalDateTime updatedAt;
  private String lastError;
}
