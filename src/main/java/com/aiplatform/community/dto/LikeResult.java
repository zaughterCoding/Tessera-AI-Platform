package com.aiplatform.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LikeResult {
  private final long creationId;
  private final boolean liked;
  private final int likeCount;
}
