package com.aiplatform.generation.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.generation.dto.GenerationStatusDTO;
import com.aiplatform.generation.dto.GenerationTaskDTO;
import com.aiplatform.generation.dto.GenerationTaskForm;
import com.aiplatform.generation.service.IGenerationTaskService;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.PublicEndpoint;
import com.aiplatform.identity.security.UserHolder;
import jakarta.validation.Valid;
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

/** 本人任务入口；用户只取自服务端会话，他人任务保持 404。 */
@Validated
@RestController
@RequestMapping("/generation")
public class GenerationTaskController {
  private final IGenerationTaskService tasks;

  public GenerationTaskController(IGenerationTaskService tasks) {
    this.tasks = tasks;
  }

  @PublicEndpoint
  @GetMapping("/status")
  public Result<GenerationStatusDTO> status() {
    return Result.ok(tasks.status());
  }

  @PostMapping("/tasks")
  public Result<GenerationTaskDTO> submit(@Valid @RequestBody GenerationTaskForm form) {
    return Result.ok(tasks.submit(currentUserId(), form));
  }

  @GetMapping("/tasks")
  public Result<List<GenerationTaskDTO>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return tasks.list(currentUserId(), page, size);
  }

  @GetMapping("/tasks/{id}")
  public Result<GenerationTaskDTO> detail(@PathVariable @Positive Long id) {
    return Result.ok(tasks.detail(currentUserId(), id));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
