package com.aiplatform.entitlement.service;

import com.aiplatform.common.api.Result;
import com.aiplatform.entitlement.dto.EntitlementBalanceDTO;
import com.aiplatform.entitlement.dto.EntitlementGrantDTO;
import com.aiplatform.entitlement.dto.EntitlementLedgerDTO;
import com.aiplatform.trade.entity.PurchaseOrder;
import java.util.List;
import java.util.Map;

/** 本人只读查询；归属由服务端会话用户决定，查询绝不触发发放。 */
public interface IEntitlementQueryService {
  /** 按本人查询权益包；applicationId 非空时只返回该应用，用户身份始终来自会话。 */
  Result<List<EntitlementGrantDTO>> list(
      long userId, Long applicationId, int page, int size);

  default Result<List<EntitlementGrantDTO>> list(long userId, int page, int size) {
    return list(userId, null, page, size);
  }

  EntitlementGrantDTO detail(long userId, long grantId);

  Result<List<EntitlementLedgerDTO>> ledger(long userId, long grantId, int page, int size);

  EntitlementBalanceDTO balance(long userId);

  String noticeFor(PurchaseOrder order);

  Map<Long, String> noticesFor(List<PurchaseOrder> orders);
}
