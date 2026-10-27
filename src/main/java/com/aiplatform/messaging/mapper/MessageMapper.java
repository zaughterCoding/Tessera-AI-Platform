package com.aiplatform.messaging.mapper;

import com.aiplatform.messaging.entity.MessageFailure;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageMapper {
  int countInboxEvent(@Param("consumerGroup") String consumerGroup,
      @Param("eventId") long eventId);

  int insertInboxEvent(@Param("consumerGroup") String consumerGroup,
      @Param("eventId") long eventId);

  int insertFailure(@Param("consumerGroup") String consumerGroup,
      @Param("eventKey") String eventKey,
      @Param("eventId") Long eventId,
      @Param("brokerMessageId") String brokerMessageId,
      @Param("payload") String payload,
      @Param("reason") String reason);

  List<MessageFailure> listFailures(@Param("consumerGroup") String consumerGroup,
      @Param("limit") int limit,
      @Param("offset") int offset);
}
