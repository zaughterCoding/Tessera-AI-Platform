package com.aiplatform.entitlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 权益余额以本表为准；流水只能追加，不能用缓存或消息状态替代。 */
@Data
@TableName("entitlement_grant")
public class EntitlementGrant {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Long orderId;
  private String itemKey;
  private Long productId;
  private Long applicationId;
  private String applicationName;
  private Integer applicationVersion;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
  private Integer credits;
  private Integer validDays;
  private String sourceFunding;
  private LocalDateTime sourcePaidAt;
  private Integer totalCredits;
  private Integer availableCredits;
  private Integer frozenCredits;
  private Integer consumedCredits;
  private Integer expiredCredits;
  private LocalDateTime effectiveFrom;
  private LocalDateTime expiresAt;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
