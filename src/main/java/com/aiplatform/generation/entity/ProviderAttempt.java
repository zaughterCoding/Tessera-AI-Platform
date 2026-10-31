package com.aiplatform.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 稳定上游尝试；attemptKey 是一次任务唯一且不可更换的上游幂等身份。 */
@Data
@TableName("provider_attempt")
public class ProviderAttempt {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long taskId;
  private Long channelId;
  private String attemptKey;
  private String providerTaskId;
  private String state;
  private String owner;
  private LocalDateTime leaseUntil;
  private Long generation;
  private Integer submitAttempts;
  private Integer queryAttempts;
  private LocalDateTime nextAttemptAt;
  private LocalDateTime submittedAt;
  private LocalDateTime lastContactAt;
  private LocalDateTime terminalAt;
  private String resultRef;
  private String resultDigest;
  private String failureCode;
  private String failureDetail;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
