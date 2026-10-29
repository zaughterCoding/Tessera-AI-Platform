package com.aiplatform.messaging.mapper;

import com.aiplatform.messaging.entity.MessageFailure;
import java.time.LocalDateTime;
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

  long countFailures();

  long countInboxBefore(@Param("cutoff") LocalDateTime cutoff);

  int deleteInboxBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

  long countFailuresBefore(@Param("cutoff") LocalDateTime cutoff);

  int deleteFailuresBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
