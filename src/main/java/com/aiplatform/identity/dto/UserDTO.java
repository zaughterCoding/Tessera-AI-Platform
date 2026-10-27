package com.aiplatform.identity.dto;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserDTO {
  private final Long id;
  private final String nickName;
  private final String icon;
  private final Set<String> roles;
}
