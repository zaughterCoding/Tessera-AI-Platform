package com.aiplatform.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowResult {
  private final long userId;
  private final boolean following;
  private final long followerCount;
  private final long followingCount;
}
