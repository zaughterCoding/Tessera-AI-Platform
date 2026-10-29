package com.aiplatform.community.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.community.dto.SignInResult;
import com.aiplatform.community.service.ISignInService;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 签到身份只来自登录会话，接口不接收用户 id。 */
@RestController
@RequestMapping("/user")
public class SignInController {
  private final ISignInService signIns;

  public SignInController(ISignInService signIns) {
    this.signIns = signIns;
  }

  @PostMapping("/sign")
  public Result<SignInResult> sign() {
    return Result.ok(signIns.signIn(currentUserId()));
  }

  @GetMapping("/sign/count")
  public Result<Integer> count() {
    return Result.ok(signIns.consecutiveDays(currentUserId()));
  }

  @GetMapping("/sign/status")
  public Result<SignInResult> status() {
    return Result.ok(signIns.status(currentUserId()));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
