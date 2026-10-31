package com.aiplatform.generation.controller;

import com.aiplatform.common.api.Result;
import com.aiplatform.generation.dto.GenerationAdminActionResultDTO;
import com.aiplatform.generation.dto.GenerationAdminDeliveryDTO;
import com.aiplatform.generation.dto.GenerationAdminOverviewDTO;
import com.aiplatform.generation.dto.GenerationAdminTaskDTO;
import com.aiplatform.generation.dto.GenerationCancelForm;
import com.aiplatform.generation.dto.GenerationReconcileResultDTO;
import com.aiplatform.generation.dto.SimulatorFaultForm;
import com.aiplatform.generation.service.IGenerationAdminService;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.operations.audit.AuditOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 生成调度排障入口；恢复只提前对账时间，故障规则只影响模拟渠道。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/generation")
public class AdminGenerationController {
  private final IGenerationAdminService generations;

  public AdminGenerationController(IGenerationAdminService generations) {
    this.generations = generations;
  }

  @GetMapping("/overview")
  public Result<GenerationAdminOverviewDTO> overview() {
    return Result.ok(generations.overview());
  }

  @GetMapping("/tasks")
  public Result<List<GenerationAdminTaskDTO>> tasks(
      @RequestParam(required = false)
      @Pattern(regexp = "[A-Z_]{3,32}") String state,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    return Result.ok(generations.tasks(state, limit, offset));
  }

  @GetMapping("/tasks/{id}")
  public Result<GenerationAdminTaskDTO> task(@PathVariable @Positive Long id) {
    return Result.ok(generations.task(id));
  }

  @PostMapping("/tasks/{id}/reconcile")
  @AuditOperation(action = "generation.reconcile", objectType = "generation-task")
  public Result<GenerationReconcileResultDTO> reconcile(@PathVariable @Positive Long id) {
    return Result.ok(generations.reconcile(id));
  }

  @GetMapping("/tasks/{id}/delivery")
  public Result<GenerationAdminDeliveryDTO> delivery(@PathVariable @Positive Long id) {
    return Result.ok(generations.delivery(id));
  }

  @PostMapping("/tasks/{id}/results/retry")
  @AuditOperation(action = "generation.result-retry", objectType = "generation-task")
  public Result<GenerationAdminActionResultDTO> retryResult(@PathVariable @Positive Long id) {
    return Result.ok(generations.retryResult(id));
  }

  @PostMapping("/tasks/{id}/settlement/recover")
  @AuditOperation(action = "generation.settlement-recover", objectType = "generation-task")
  public Result<GenerationAdminActionResultDTO> recoverSettlement(
      @PathVariable @Positive Long id) {
    return Result.ok(generations.recoverSettlement(id));
  }

  @PostMapping("/tasks/{id}/cancel")
  @AuditOperation(action = "generation.cancel-request", objectType = "generation-task")
  public Result<GenerationAdminActionResultDTO> cancel(@PathVariable @Positive Long id,
      @RequestBody(required = false) GenerationCancelForm form) {
    return Result.ok(generations.cancelTask(id, form == null ? null : form.getReason()));
  }

  @PostMapping("/simulator/faults")
  @AuditOperation(action = "generation.simulator-fault", objectType = "generation-simulator",
      objectIdParam = "")
  public Result<Void> fault(@Valid @RequestBody SimulatorFaultForm form) {
    generations.addSimulatorFault(form);
    return Result.ok();
  }
}
