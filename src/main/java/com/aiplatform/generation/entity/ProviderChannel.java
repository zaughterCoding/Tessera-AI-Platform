package com.aiplatform.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 上游渠道容量行；计数字段只由条件更新维护。 */
@Data
@TableName("provider_channel")
public class ProviderChannel {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String code;
  private String displayName;
  private String kind;
  private String status;
  private Integer maxInFlight;
  private Integer inFlight;
  private Integer effectiveInFlight;
  private LocalDateTime dispatchPausedUntil;
  private LocalDateTime adaptiveWindowAt;
  private Integer queueCapacity;
  private Integer queued;
  private Integer queueTimeoutSeconds;
  private Boolean supportsIdempotentSubmit;
  private Boolean supportsQueryByAttempt;
  private Boolean supportsCancel;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
