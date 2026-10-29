package com.aiplatform.community.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.community.dto.CreationForm;
import com.aiplatform.community.dto.CreationPage;
import com.aiplatform.community.dto.CreationUpdateForm;
import com.aiplatform.community.dto.CreationVisibilityForm;
import com.aiplatform.community.dto.FeedResult;
import com.aiplatform.community.dto.LikeResult;
import com.aiplatform.community.service.ICreationService;
import com.aiplatform.community.service.IFeedService;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.PublicEndpoint;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.identity.service.IUserService;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 公开列表只返回已发布作品；私有详情只走本人端点。 */
@Validated
@RestController
@RequestMapping("/creations")
public class CreationController {
  private final ICreationService creations;
  private final IFeedService feed;
  private final IUserService users;

  public CreationController(ICreationService creations, IFeedService feed, IUserService users) {
    this.creations = creations;
    this.feed = feed;
    this.users = users;
  }

  @PublicEndpoint
  @GetMapping
  public Result<List<CreationDTO>> list(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    CreationPage page = creations.publicPage(optionalUserId(authorization), limit, offset);
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @PublicEndpoint
  @GetMapping("/hot")
  public Result<List<CreationDTO>> hot(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    CreationPage page = creations.hotPage(optionalUserId(authorization), limit, offset);
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @PublicEndpoint
  @GetMapping("/by-application/{applicationId}")
  public Result<List<CreationDTO>> byApplication(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable @Positive Long applicationId,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    CreationPage page = creations.applicationPage(optionalUserId(authorization),
        applicationId, limit, offset);
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @PublicEndpoint
  @GetMapping("/by-user/{userId}")
  public Result<List<CreationDTO>> byUser(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable @Positive Long userId,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    CreationPage page = creations.authorPage(optionalUserId(authorization), userId, limit, offset);
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @PublicEndpoint
  @GetMapping("/{id}")
  public Result<CreationDTO> detail(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable @Positive Long id) {
    return Result.ok(creations.publicDetail(optionalUserId(authorization), id));
  }

  @GetMapping("/feed")
  public Result<FeedResult> feed(
      @RequestParam(required = false) Long lastTime,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "10") int size) {
    return Result.ok(feed.feed(currentUserId(), lastTime, offset, size));
  }

  @GetMapping("/mine")
  public Result<List<CreationDTO>> mine(
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    CreationPage page = creations.mine(currentUserId(), limit, offset);
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @GetMapping("/mine/{id}")
  public Result<CreationDTO> mineDetail(@PathVariable @Positive Long id) {
    return Result.ok(creations.ownedDetail(currentUserId(), id));
  }

  @PostMapping
  public Result<CreationDTO> create(@Valid @RequestBody CreationForm form) {
    return Result.ok(creations.create(currentUserId(), form));
  }

  @PutMapping("/{id}")
  public Result<CreationDTO> update(
      @PathVariable @Positive Long id,
      @Valid @RequestBody CreationUpdateForm form) {
    return Result.ok(creations.update(currentUserId(), id, form));
  }

  @PutMapping("/{id}/visibility")
  public Result<CreationDTO> visibility(
      @PathVariable @Positive Long id,
      @Valid @RequestBody CreationVisibilityForm form) {
    return Result.ok(creations.setVisibility(currentUserId(), id, form.getPublished()));
  }

  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable @Positive Long id) {
    creations.delete(currentUserId(), id);
    return Result.ok();
  }

  @PutMapping("/{id}/likes/me")
  public Result<LikeResult> like(@PathVariable @Positive Long id) {
    return Result.ok(creations.setLiked(currentUserId(), id, true));
  }

  @DeleteMapping("/{id}/likes/me")
  public Result<LikeResult> unlike(@PathVariable @Positive Long id) {
    return Result.ok(creations.setLiked(currentUserId(), id, false));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }

  private Long optionalUserId(String authorization) {
    if (authorization == null || authorization.trim().isEmpty()) {
      return null;
    }
    UserDTO user = users.authenticate(authorization);
    return user == null ? null : user.getId();
  }
}
