package com.aiplatform.cache;

import java.time.LocalDateTime;
import lombok.Data;

/** 最近一次完成的 CDC 批次；只用于审计与延迟观测。 */
@Data
public class CacheCheckpoint {
  private String destination;
  private Long batchId;
  private String binlogFile;
  private Long binlogOffset;
  private Long eventTimeMs;
  private LocalDateTime processedAt;
}
