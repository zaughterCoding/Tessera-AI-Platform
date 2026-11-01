package com.aiplatform.generation.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.generation.dto.GenerationCancelForm;
import com.aiplatform.generation.dto.GenerationShareForm;
import com.aiplatform.generation.dto.GenerationStatusDTO;
import com.aiplatform.generation.dto.GenerationTaskDTO;
import com.aiplatform.generation.dto.GenerationTaskForm;
import com.aiplatform.generation.service.GenerationShareService;
import com.aiplatform.generation.service.IGenerationTaskService;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.PublicEndpoint;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.media.dto.MediaContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
  private final GenerationShareService sharing;

  public GenerationTaskController(IGenerationTaskService tasks, GenerationShareService sharing) {
    this.tasks = tasks;
    this.sharing = sharing;
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

  /** 私有结果只从服务端登记的文件写出，不接受客户端提供的地址或路径。 */
  @GetMapping("/tasks/{id}/results/{resultId}/content")
  public ResponseEntity<Resource> downloadResult(@PathVariable @Positive Long id,
      @PathVariable @Positive Long resultId) {
    MediaContent content = tasks.downloadResult(currentUserId(), id, resultId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.getContentType()))
        .contentLength(content.getSizeBytes())
        .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).cachePrivate())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"generation-result-" + resultId + "\""
                + extension(content.getContentType()))
        .header("X-Content-Type-Options", "nosniff")
        .body(content.getResource());
  }

  /** 用户显式选择结果后创建作品；默认私有，公开必须显式确认。 */
  @PostMapping("/tasks/{id}/share")
  public Result<CreationDTO> share(@PathVariable @Positive Long id,
      @Valid @RequestBody GenerationShareForm form) {
    return Result.ok(sharing.share(currentUserId(), id, form));
  }

  @PostMapping("/tasks/{id}/cancel")
  public Result<GenerationTaskDTO> cancel(@PathVariable @Positive Long id,
      @RequestBody(required = false) GenerationCancelForm form) {
    return Result.ok(tasks.cancel(currentUserId(), id, form == null ? null : form.getReason()));
  }

  private String extension(String contentType) {
    if ("image/png".equals(contentType)) {
      return ".png";
    }
    if ("image/jpeg".equals(contentType)) {
      return ".jpg";
    }
    if ("image/webp".equals(contentType)) {
      return ".webp";
    }
    return "";
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
