package com.aiplatform.entitlement.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.entitlement.dto.EntitlementBalanceDTO;
import com.aiplatform.entitlement.dto.EntitlementGrantDTO;
import com.aiplatform.entitlement.dto.EntitlementLedgerDTO;
import com.aiplatform.entitlement.service.IEntitlementQueryService;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.UserHolder;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 本人权益入口；用户身份只取自服务端会话，他人资源保持 404。 */
@Validated
@RestController
@RequestMapping("/entitlement")
public class EntitlementController {
  private final IEntitlementQueryService entitlements;

  public EntitlementController(IEntitlementQueryService entitlements) {
    this.entitlements = entitlements;
  }

  @GetMapping("/grants")
  public Result<List<EntitlementGrantDTO>> grants(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) @Positive Long applicationId) {
    return entitlements.list(currentUserId(), applicationId, page, size);
  }

  @GetMapping("/grants/{id}")
  public Result<EntitlementGrantDTO> detail(@PathVariable @Positive Long id) {
    return Result.ok(entitlements.detail(currentUserId(), id));
  }

  @GetMapping("/grants/{id}/ledger")
  public Result<List<EntitlementLedgerDTO>> ledger(
      @PathVariable @Positive Long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return entitlements.ledger(currentUserId(), id, page, size);
  }

  @GetMapping("/balance")
  public Result<EntitlementBalanceDTO> balance() {
    return Result.ok(entitlements.balance(currentUserId()));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
