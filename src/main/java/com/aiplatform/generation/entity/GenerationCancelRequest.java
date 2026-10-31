package com.aiplatform.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 取消是持久请求而不是瞬时结果；只有可信终态才能完成释放。 */
@Data
@TableName("generation_cancel_request")
public class GenerationCancelRequest {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long taskId;
  private Long userId;
  private String state;
  private String outcome;
  private String reason;
  private Integer attempts;
  private String owner;
  private LocalDateTime leaseUntil;
  private Long generation;
  private LocalDateTime nextAttemptAt;
  private String lastError;
  private LocalDateTime requestedAt;
  private LocalDateTime resolvedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
