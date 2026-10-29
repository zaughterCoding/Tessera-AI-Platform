package com.aiplatform.community.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 点赞事实以唯一键为准，重复写入不产生第二次计数。 */
@Data
public class CreationLike {
  private Long creationId;
  private Long userId;
  private LocalDateTime createdAt;
}
