package com.aiplatform.identity.controller;

import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.LoginFormDTO;
import com.aiplatform.identity.dto.ProfileFormDTO;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.PublicEndpoint;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.identity.service.IUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/user")
public class UserController {
  private final IUserService users;

  public UserController(IUserService users) {
    this.users = users;
  }

  @PublicEndpoint(rateLimited = true)
  @PostMapping("/code")
  public Result<Map<String, String>> code(
      @RequestParam @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {
    return users.sendCode(phone);
  }

  @PublicEndpoint(rateLimited = true)
  @PostMapping("/login")
  public Result<String> login(@RequestBody @Valid LoginFormDTO form) {
    return users.login(form);
  }

  @PostMapping("/logout")
  public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
    return users.logout(authorization);
  }

  @GetMapping("/me")
  public Result<UserDTO> me() {
    return Result.ok(UserHolder.getUser());
  }

  @GetMapping("/{id}")
  public Result<UserDTO> byId(@PathVariable @Positive Long id) {
    return users.queryUserById(id);
  }

  @PutMapping("/me")
  public Result<Void> update(@RequestBody @Valid ProfileFormDTO form) {
    return users.updateProfile(form);
  }
}
