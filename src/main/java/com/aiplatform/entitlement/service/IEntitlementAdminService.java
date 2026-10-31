package com.aiplatform.entitlement.service;

import com.aiplatform.entitlement.dto.EntitlementAnomalyDTO;
import com.aiplatform.entitlement.dto.EntitlementBackfillDTO;
import com.aiplatform.entitlement.dto.EntitlementBackfillForm;
import com.aiplatform.entitlement.dto.EntitlementReconciliationDTO;
import java.util.List;

/** 受 ADMIN 请求权限与服务方法权限双重保护的补发、到期和对账入口。 */
public interface IEntitlementAdminService {
  EntitlementBackfillDTO backfill(EntitlementBackfillForm form);

  EntitlementReconciliationDTO reconcile();

  int expire(int limit);

  List<EntitlementAnomalyDTO> anomalies(String status, int limit);
}
