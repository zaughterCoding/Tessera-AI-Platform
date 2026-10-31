package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.ProviderCallbackEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProviderCallbackMapper {
  int insertInboxIgnore(@Param("channelId") long channelId, @Param("eventKey") String eventKey,
      @Param("eventType") String eventType, @Param("attemptId") long attemptId,
      @Param("payloadDigest") String payloadDigest, @Param("payloadSize") int payloadSize,
      @Param("outcome") String outcome);

  ProviderCallbackEvent selectInboxForUpdate(@Param("channelId") long channelId,
      @Param("eventKey") String eventKey);

  int updateInboxOutcome(@Param("id") long id, @Param("outcome") String outcome);

  int insertConflictIgnore(@Param("channelId") long channelId,
      @Param("eventKey") String eventKey, @Param("existingDigest") String existingDigest,
      @Param("incomingDigest") String incomingDigest, @Param("payloadSize") int payloadSize);

  int countConflicts();

  List<ProviderCallbackEvent> selectRecent(@Param("limit") int limit);
}
