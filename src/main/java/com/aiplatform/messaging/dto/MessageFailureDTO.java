package com.aiplatform.messaging.dto;

import com.aiplatform.messaging.entity.MessageFailure;
import java.time.LocalDateTime;
import lombok.Data;

/** 失败定位视图；原始载荷仅保留在本地表中，不通过接口扩散。 */
@Data
public class MessageFailureDTO {
  private String consumerGroup;
  private String eventKey;
  private Long eventId;
  private String brokerMessageId;
  private String reason;
  private LocalDateTime createdAt;

  public static MessageFailureDTO from(MessageFailure failure) {
    MessageFailureDTO dto = new MessageFailureDTO();
    dto.setConsumerGroup(failure.getConsumerGroup());
    dto.setEventKey(failure.getEventKey());
    dto.setEventId(failure.getEventId());
    dto.setBrokerMessageId(failure.getBrokerMessageId());
    dto.setReason(failure.getReason());
    dto.setCreatedAt(failure.getCreatedAt());
    return dto;
  }
}
