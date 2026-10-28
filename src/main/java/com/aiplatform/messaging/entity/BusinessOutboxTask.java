package com.aiplatform.messaging.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 业务事实的持久任务；消息只是投递手段，状态以本表为准。 */
@Data
@TableName("business_outbox")
public class BusinessOutboxTask {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String kind;
  private Long aggregateId;
  private Long userId;
  private String state;
  private Integer attempts;
  private Integer consumeAttempts;
  private LocalDateTime nextAttemptAt;
  private LocalDateTime deliverAt;
  private String idempotencyKey;
  private String leaseOwner;
  private LocalDateTime leaseUntil;
  private LocalDateTime sentAt;
  private LocalDateTime consumedAt;
  private String lastError;
  private LocalDateTime createdAt;
}
