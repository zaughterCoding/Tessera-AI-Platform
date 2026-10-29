package com.aiplatform.analytics.dto;

import com.aiplatform.analytics.entity.AnalyticsFailureRecord;
import java.time.LocalDateTime;
import lombok.Data;

/** 管理复核视图；原始载荷仅保留在本地表，不通过接口扩散。 */
@Data
public class AnalyticsFailureDTO {
  private String topic;
  private Integer partition;
  private Long offset;
  private String reason;
  private LocalDateTime createdAt;

  public static AnalyticsFailureDTO from(AnalyticsFailureRecord failure) {
    AnalyticsFailureDTO dto = new AnalyticsFailureDTO();
    dto.setTopic(failure.getTopic());
    dto.setPartition(failure.getPartitionId());
    dto.setOffset(failure.getMessageOffset());
    dto.setReason(failure.getReason());
    dto.setCreatedAt(failure.getCreatedAt());
    return dto;
  }
}
