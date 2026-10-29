package com.aiplatform.community.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 发布与媒体的显式引用；只要存在引用，素材就不能被删除。 */
@Data
public class CreationMedia {
  private Long creationId;
  private Long mediaId;
  private Integer position;
  private LocalDateTime createdAt;
}
