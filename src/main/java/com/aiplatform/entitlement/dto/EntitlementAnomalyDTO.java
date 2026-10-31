package com.aiplatform.entitlement.dto;

import com.aiplatform.entitlement.entity.EntitlementAnomaly;
import java.time.LocalDateTime;
import lombok.Data;

/** 管理端异常视图；异常不会被自动当成“当前时间”发放。 */
@Data
public class EntitlementAnomalyDTO {
  private Long id;
  private Long orderId;
  private Long userId;
  private String itemKey;
  private String anomalyType;
  private String detail;
  private String status;
  private LocalDateTime resolvedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static EntitlementAnomalyDTO from(EntitlementAnomaly anomaly) {
    EntitlementAnomalyDTO view = new EntitlementAnomalyDTO();
    view.setId(anomaly.getId());
    view.setOrderId(anomaly.getOrderId());
    view.setUserId(anomaly.getUserId());
    view.setItemKey(anomaly.getItemKey());
    view.setAnomalyType(anomaly.getAnomalyType());
    view.setDetail(anomaly.getDetail());
    view.setStatus(anomaly.getStatus());
    view.setResolvedAt(anomaly.getResolvedAt());
    view.setCreatedAt(anomaly.getCreatedAt());
    view.setUpdatedAt(anomaly.getUpdatedAt());
    return view;
  }
}
