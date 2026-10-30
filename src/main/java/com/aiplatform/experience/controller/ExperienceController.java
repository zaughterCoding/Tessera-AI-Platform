package com.aiplatform.experience.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.experience.dto.CacheTraceDTO;
import com.aiplatform.experience.dto.OrderProgressDTO;
import com.aiplatform.experience.service.IExperienceService;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.identity.security.UserHolder;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 技术体验入口只在管理身份下开放，并由 app.experience.enabled 双重把关。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/experience")
public class ExperienceController {
  private final IExperienceService experience;

  public ExperienceController(IExperienceService experience) {
    this.experience = experience;
  }

  @GetMapping("/capabilities")
  public Result<Map<String, Object>> capabilities() {
    return Result.ok(experience.capabilities());
  }

  @GetMapping("/cache-trace/{applicationId}")
  public Result<CacheTraceDTO> traceCache(@PathVariable @Positive Long applicationId) {
    return Result.ok(experience.traceCache(applicationId));
  }

  @GetMapping("/orders/{orderId}/progress")
  public Result<OrderProgressDTO> orderProgress(@PathVariable @Positive Long orderId) {
    return Result.ok(experience.orderProgress(currentUserId(), orderId));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
