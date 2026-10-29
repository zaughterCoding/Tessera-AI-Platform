package com.aiplatform.community.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.community.dto.FollowResult;
import com.aiplatform.community.dto.FollowUserDTO;
import com.aiplatform.community.service.IFollowService;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.UserHolder;
import java.util.List;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 关注关系以服务端当前用户为主动方，目标用户不能由请求体伪造。 */
@Validated
@RestController
@RequestMapping("/follows")
public class FollowController {
  private final IFollowService follows;

  public FollowController(IFollowService follows) {
    this.follows = follows;
  }

  @PutMapping("/{userId}")
  public Result<FollowResult> follow(@PathVariable @Positive Long userId) {
    return Result.ok(follows.follow(currentUserId(), userId));
  }

  @DeleteMapping("/{userId}")
  public Result<FollowResult> unfollow(@PathVariable @Positive Long userId) {
    return Result.ok(follows.unfollow(currentUserId(), userId));
  }

  @GetMapping("/{userId}/status")
  public Result<FollowResult> status(@PathVariable @Positive Long userId) {
    return Result.ok(follows.status(currentUserId(), userId));
  }

  @GetMapping("/common/{userId}")
  public Result<List<FollowUserDTO>> common(@PathVariable @Positive Long userId) {
    return Result.ok(follows.common(currentUserId(), userId));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
