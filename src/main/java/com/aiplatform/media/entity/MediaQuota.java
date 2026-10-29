package com.aiplatform.media.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户累计配额；预占与释放在同一行上条件更新，避免并发绕过上限。 */
@Data
@TableName("media_quota")
public class MediaQuota {
  @TableId
  private Long userId;
  private Long usedBytes;
  private Integer assetCount;
  private LocalDateTime updatedAt;
}
