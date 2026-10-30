package com.aiplatform.identity.service;

import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.LoginFormDTO;
import com.aiplatform.identity.dto.ProfileFormDTO;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.entity.User;
import com.baomidou.mybatisplus.spring.service.IService;
import java.util.Map;

public interface IUserService extends IService<User> {
  Result<Map<String, String>> sendCode(String phone);

  Result<String> login(LoginFormDTO form);

  Result<Void> logout(String authorization);

  UserDTO authenticate(String authorization);

  Result<UserDTO> queryUserById(Long userId);

  Result<Void> updateProfile(ProfileFormDTO form);

  UserDTO findForAdministration(Long userId);
}
