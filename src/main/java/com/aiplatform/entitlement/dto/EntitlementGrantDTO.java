package com.aiplatform.entitlement.dto;

import com.aiplatform.entitlement.entity.EntitlementGrant;
import java.time.LocalDateTime;
import lombok.Data;

/** 查询时按数据库时间展示有效余额；到期任务尚未清理也不把过期次数当成可用。 */
@Data
public class EntitlementGrantDTO {
  private static final String ACTIVE = "ACTIVE";
  private static final String EXPIRED = "EXPIRED";

  private Long id;
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

  public static EntitlementGrantDTO from(EntitlementGrant grant, LocalDateTime now) {
    EntitlementGrantDTO view = new EntitlementGrantDTO();
    view.setId(grant.getId());
    view.setOrderId(grant.getOrderId());
    view.setItemKey(grant.getItemKey());
    view.setProductId(grant.getProductId());
    view.setApplicationId(grant.getApplicationId());
    view.setApplicationName(grant.getApplicationName());
    view.setApplicationVersion(grant.getApplicationVersion());
    view.setModelVersion(grant.getModelVersion());
    view.setImageSize(grant.getImageSize());
    view.setOutputCount(grant.getOutputCount());
    view.setCredits(grant.getCredits());
    view.setValidDays(grant.getValidDays());
    view.setSourceFunding(grant.getSourceFunding());
    view.setSourcePaidAt(grant.getSourcePaidAt());
    // 已到期或已标记过期的权益包都不再展示可用次数；尚未执行的清理不能扩大余额。
    boolean expired = EXPIRED.equals(grant.getStatus())
        || !grant.getExpiresAt().isAfter(now);
    int available = expired ? 0 : grant.getAvailableCredits();
    int expiredCredits = grant.getExpiredCredits() + (expired ? grant.getAvailableCredits() : 0);
    view.setTotalCredits(grant.getTotalCredits());
    view.setAvailableCredits(available);
    view.setFrozenCredits(grant.getFrozenCredits());
    view.setConsumedCredits(grant.getConsumedCredits());
    view.setExpiredCredits(expiredCredits);
    view.setEffectiveFrom(grant.getEffectiveFrom());
    view.setExpiresAt(grant.getExpiresAt());
    view.setStatus(expired ? EXPIRED : ACTIVE);
    view.setCreatedAt(grant.getCreatedAt());
    return view;
  }
}
