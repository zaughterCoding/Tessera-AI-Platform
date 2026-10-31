package com.aiplatform.entitlement.dto;

import com.aiplatform.entitlement.entity.EntitlementLedger;
import java.time.LocalDateTime;
import lombok.Data;

/** 流水视图保留各分量增量与操作后余额，便于从发放事实重建当前余额。 */
@Data
public class EntitlementLedgerDTO {
  private Long id;
  private Long grantId;
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

  public static EntitlementLedgerDTO from(EntitlementLedger ledger) {
    EntitlementLedgerDTO view = new EntitlementLedgerDTO();
    view.setId(ledger.getId());
    view.setGrantId(ledger.getGrantId());
    view.setOperationType(ledger.getOperationType());
    view.setDeltaTotal(ledger.getDeltaTotal());
    view.setDeltaAvailable(ledger.getDeltaAvailable());
    view.setDeltaFrozen(ledger.getDeltaFrozen());
    view.setDeltaConsumed(ledger.getDeltaConsumed());
    view.setDeltaExpired(ledger.getDeltaExpired());
    view.setTotalAfter(ledger.getTotalAfter());
    view.setAvailableAfter(ledger.getAvailableAfter());
    view.setFrozenAfter(ledger.getFrozenAfter());
    view.setConsumedAfter(ledger.getConsumedAfter());
    view.setExpiredAfter(ledger.getExpiredAfter());
    view.setReason(ledger.getReason());
    view.setCreatedAt(ledger.getCreatedAt());
    return view;
  }
}
