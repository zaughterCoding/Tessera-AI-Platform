package com.aiplatform.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 交接责任；COMPLETED 只表示责任已转移到持久尝试行，不表示上游完成。 */
@Data
@TableName("generation_task_handoff")
public class GenerationHandoff {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long taskId;
  private String state;
  private String claimOwner;
  private LocalDateTime leaseUntil;
  private Long generation;
  private Integer attempts;
  private LocalDateTime nextAttemptAt;
  private LocalDateTime enqueuedAt;
  private LocalDateTime completedAt;
  private String completionReason;
  private String lastError;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
