package com.aiplatform.community.service;

import com.aiplatform.community.dto.FeedResult;
import com.aiplatform.messaging.ProjectionResult;

public interface IFeedService {
  /** 按粉丝边游标分批写入；重复投递只重复写相同的 ZSet 成员。 */
  ProjectionResult fanout(long creationId, long authorId, long afterFollowId);

  FeedResult feed(long userId, Long lastTime, int offset, int size);
}
