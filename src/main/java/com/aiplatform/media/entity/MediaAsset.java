package com.aiplatform.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 媒体登记事实；文件是否已完整落盘由 state 而不是数据库事务保证。 */
@Data
@TableName("media_asset")
public class MediaAsset {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private String path;
  private String originalName;
  private String contentType;
  private Long sizeBytes;
  private String state;
  private String source;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;
}
