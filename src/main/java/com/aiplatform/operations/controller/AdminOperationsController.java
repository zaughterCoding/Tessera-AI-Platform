package com.aiplatform.operations.controller;

import com.aiplatform.common.api.Result;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.operations.audit.AuditOperation;
import com.aiplatform.operations.dto.OperationAuditDTO;
import com.aiplatform.operations.dto.RetentionOverviewDTO;
import com.aiplatform.operations.dto.RetentionRunDTO;
import com.aiplatform.operations.dto.RetentionRunForm;
import com.aiplatform.operations.retention.RetentionTarget;
import com.aiplatform.operations.service.IOperationAuditService;
import com.aiplatform.operations.service.IRetentionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审计查询与保留清理入口；清理只接受显式目标，并在审计中留下结果。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/operations")
public class AdminOperationsController {
  private final IOperationAuditService audits;
  private final IRetentionService retention;

  public AdminOperationsController(
      IOperationAuditService audits, IRetentionService retention) {
    this.audits = audits;
    this.retention = retention;
  }

  @GetMapping("/audit")
  public Result<List<OperationAuditDTO>> audit(
      @RequestParam(required = false) @Pattern(regexp = "[a-z0-9._-]{1,64}") String action,
      @RequestParam(required = false) @Pattern(regexp = "[a-z0-9._-]{1,64}") String objectType,
      @RequestParam(required = false) Long operatorId,
      @RequestParam(required = false) @Pattern(regexp = "SUCCESS|FAILURE") String outcome,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return audits.list(action, objectType, operatorId, outcome, limit, offset);
  }

  @GetMapping("/retention")
  public Result<RetentionOverviewDTO> retention() {
    return Result.ok(retention.overview());
  }

  @PostMapping("/retention/{target}/run")
  @AuditOperation(action = "retention.run", objectType = "retention",
      objectIdParam = "target")
  public Result<RetentionRunDTO> run(
      @PathVariable
      @Pattern(regexp = "business-outbox-done|message-inbox|message-failure"
          + "|analytics-failure|operation-audit") String target,
      @Valid @RequestBody(required = false) RetentionRunForm form) {
    int batches = form == null || form.getMaxBatches() == null ? 10 : form.getMaxBatches();
    return Result.ok(retention.run(RetentionTarget.fromKey(target), batches));
  }
}
