package com.aiplatform.operations.dto;

import lombok.Data;

@Data
public class MediaRetentionDTO {
  private String target = "media-files";
  private String label = "媒体文件";
  private String rule = "超时 PENDING 回滚配额；孤儿文件超过宽限期清理；DELETED 登记保留";
  private long pendingGraceSeconds;
  private long orphanGraceSeconds;
  private long pending;
  private long active;
  private long deleting;
  private long deleted;
}
