package com.aiplatform.community.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** user_id 是粉丝，follow_user_id 是被关注者；自增 id 用于粉丝分批游标。 */
@Data
public class FollowEdge {
  private Long id;
  private Long userId;
  private Long followUserId;
  private LocalDateTime createdAt;
}
