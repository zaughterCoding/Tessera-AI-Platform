package com.aiplatform.generation.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 已验证回调的收件记录；摘要用于区分重复与同身份冲突。 */
@Data
public class ProviderCallbackEvent {
  private Long id;
  private Long channelId;
  private String eventKey;
  private String eventType;
  private Long attemptId;
  private String payloadDigest;
  private Integer payloadSize;
  private String outcome;
  private LocalDateTime receivedAt;
}
