package com.aiplatform.identity.controller;

import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.identity.service.IUserService;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {
  private final IUserService users;

  public AdminUserController(IUserService users) {
    this.users = users;
  }

  @GetMapping("/{id}/roles")
  public Result<UserDTO> roles(@PathVariable @Positive Long id) {
    return Result.ok(users.findForAdministration(id));
  }
}
