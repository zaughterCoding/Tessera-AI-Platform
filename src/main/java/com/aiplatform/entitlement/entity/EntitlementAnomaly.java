package com.aiplatform.entitlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 无法自动发放的历史来源保留在这里，等待补齐可信事实后重新补发。 */
@Data
@TableName("entitlement_anomaly")
public class EntitlementAnomaly {
  @TableId(type = IdType.AUTO)
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
}
