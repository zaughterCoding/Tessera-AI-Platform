package com.aiplatform.community.service;

import com.aiplatform.community.dto.FollowResult;
import com.aiplatform.community.dto.FollowUserDTO;
import java.util.List;

public interface IFollowService {
  FollowResult follow(long userId, long targetUserId);

  FollowResult unfollow(long userId, long targetUserId);

  FollowResult status(long userId, long targetUserId);

  List<FollowUserDTO> common(long userId, long otherUserId);
}
