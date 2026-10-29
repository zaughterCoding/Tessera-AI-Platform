package com.aiplatform.media.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.PublicEndpoint;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.media.dto.MediaAssetDTO;
import com.aiplatform.media.dto.MediaContent;
import com.aiplatform.media.dto.MediaQuotaDTO;
import com.aiplatform.media.service.IMediaService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 私有素材与公开图片分开端点；公开读取只认已发布作品的显式引用。 */
@Validated
@RestController
@RequestMapping("/media")
public class MediaController {
  private final IMediaService media;

  public MediaController(IMediaService media) {
    this.media = media;
  }

  @PostMapping
  public Result<MediaAssetDTO> upload(@RequestPart("file") MultipartFile file) {
    return Result.ok(media.upload(currentUserId(), file));
  }

  @GetMapping("/quota")
  public Result<MediaQuotaDTO> quota() {
    return Result.ok(media.quota(currentUserId()));
  }

  @GetMapping("/mine")
  public Result<List<MediaAssetDTO>> mine(
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return Result.ok(media.listOwned(currentUserId(), limit, offset));
  }

  @GetMapping("/mine/{id}")
  public ResponseEntity<Resource> owned(@PathVariable @Positive Long id) {
    return privateFile(media.openOwned(currentUserId(), id));
  }

  @PublicEndpoint
  @GetMapping("/public/{id}")
  public ResponseEntity<Resource> shared(@PathVariable @Positive Long id) {
    return publicFile(media.openPublic(id));
  }

  @DeleteMapping("/{id}")
  public Result<MediaAssetDTO> delete(@PathVariable @Positive Long id) {
    return Result.ok(media.delete(currentUserId(), id));
  }

  private ResponseEntity<Resource> privateFile(MediaContent content) {
    return file(content, CacheControl.maxAge(0, TimeUnit.SECONDS).cachePrivate());
  }

  private ResponseEntity<Resource> publicFile(MediaContent content) {
    return file(content, CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic());
  }

  private ResponseEntity<Resource> file(MediaContent content, CacheControl cache) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.getContentType()))
        .contentLength(content.getSizeBytes())
        .cacheControl(cache)
        .header("X-Content-Type-Options", "nosniff")
        .body(content.getResource());
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
