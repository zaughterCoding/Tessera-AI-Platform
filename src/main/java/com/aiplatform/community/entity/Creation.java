package com.aiplatform.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 作品或应用评测；公开与否只由 state 决定，不靠页面隐藏。 */
@Data
@TableName("creation")
public class Creation {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Long applicationId;
  private String kind;
  private String title;
  private String content;
  private String state;
  private Integer likeCount;
  private LocalDateTime publishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
