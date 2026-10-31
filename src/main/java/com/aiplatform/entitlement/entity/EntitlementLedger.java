package com.aiplatform.entitlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 只追加的账务事实；发放、到期和后续更正都通过新增流水表达。 */
@Data
@TableName("entitlement_ledger")
public class EntitlementLedger {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long grantId;
  private Long userId;
  private Long orderId;
  private String itemKey;
  private String operationKey;
  private String operationType;
  private Integer deltaTotal;
  private Integer deltaAvailable;
  private Integer deltaFrozen;
  private Integer deltaConsumed;
  private Integer deltaExpired;
  private Integer totalAfter;
  private Integer availableAfter;
  private Integer frozenAfter;
  private Integer consumedAfter;
  private Integer expiredAfter;
  private String reason;
  private LocalDateTime createdAt;
}
