package com.aiplatform.entitlement.controller;

import com.aiplatform.common.api.Result;
import com.aiplatform.entitlement.dto.EntitlementAnomalyDTO;
import com.aiplatform.entitlement.dto.EntitlementBackfillDTO;
import com.aiplatform.entitlement.dto.EntitlementBackfillForm;
import com.aiplatform.entitlement.dto.EntitlementReconciliationDTO;
import com.aiplatform.entitlement.service.IEntitlementAdminService;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.operations.audit.AuditOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理入口不接收用户、次数、规格或有效期，只能按订单身份补发。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/entitlement")
public class AdminEntitlementController {
  private final IEntitlementAdminService entitlements;

  public AdminEntitlementController(IEntitlementAdminService entitlements) {
    this.entitlements = entitlements;
  }

  @PostMapping("/backfill")
  @AuditOperation(action = "entitlement.backfill", objectType = "entitlement-grant",
      objectIdParam = "")
  public Result<EntitlementBackfillDTO> backfill(
      @Valid @RequestBody(required = false) EntitlementBackfillForm form) {
    return Result.ok(entitlements.backfill(form));
  }

  @GetMapping("/reconciliation")
  public Result<EntitlementReconciliationDTO> reconciliation() {
    return Result.ok(entitlements.reconcile());
  }

  @PostMapping("/expire")
  @AuditOperation(action = "entitlement.expire", objectType = "entitlement-grant",
      objectIdParam = "")
  public Result<Integer> expire(
      @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
    return Result.ok(entitlements.expire(limit));
  }

  @GetMapping("/anomalies")
  public Result<List<EntitlementAnomalyDTO>> anomalies(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    return Result.ok(entitlements.anomalies(status, limit));
  }
}
