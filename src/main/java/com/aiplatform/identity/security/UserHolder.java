package com.aiplatform.identity.security;

import com.aiplatform.identity.dto.UserDTO;

public final class UserHolder {
  private static final ThreadLocal<UserDTO> USER = new ThreadLocal<>();

  private UserHolder() {}

  public static UserDTO getUser() {
    return USER.get();
  }

  public static void saveUser(UserDTO user) {
    USER.set(user);
  }

  public static void removeUser() {
    USER.remove();
  }
}
