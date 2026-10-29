package com.aiplatform.community.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.community.dto.FollowResult;
import com.aiplatform.community.dto.FollowUserDTO;
import com.aiplatform.community.mapper.FollowMapper;
import com.aiplatform.community.service.IFollowService;
import com.aiplatform.identity.mapper.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 关注与取关都是幂等目标状态；被关注者身份只从服务端当前用户推导。 */
@Service
public class FollowServiceImpl implements IFollowService {
  private final FollowMapper follows;
  private final UserMapper users;

  public FollowServiceImpl(FollowMapper follows, UserMapper users) {
    this.follows = follows;
    this.users = users;
  }

  @Override
  @Transactional
  public FollowResult follow(long userId, long targetUserId) {
    requireTarget(userId, targetUserId);
    follows.insertIgnore(userId, targetUserId);
    return status(userId, targetUserId);
  }

  @Override
  @Transactional
  public FollowResult unfollow(long userId, long targetUserId) {
    requireTarget(userId, targetUserId);
    follows.delete(userId, targetUserId);
    return status(userId, targetUserId);
  }

  @Override
  public FollowResult status(long userId, long targetUserId) {
    requireTarget(userId, targetUserId);
    return new FollowResult(targetUserId, follows.exists(userId, targetUserId) > 0,
        follows.countFollowers(targetUserId), follows.countFollowing(targetUserId));
  }

  @Override
  public List<FollowUserDTO> common(long userId, long otherUserId) {
    requireTarget(userId, otherUserId);
    return follows.selectCommonFollows(userId, otherUserId, 100);
  }

  private void requireTarget(long userId, long targetUserId) {
    if (targetUserId <= 0 || targetUserId == userId) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    if (users.selectById(targetUserId) == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
  }
}
