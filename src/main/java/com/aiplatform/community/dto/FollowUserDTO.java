package com.aiplatform.community.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowUserDTO {
  private Long userId;
  private String nickName;
  private String icon;
}
