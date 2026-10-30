package com.aiplatform.analytics.mapper;

import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 分析投递任务；租约与失败退避沿用可靠投递的既有语义。 */
@Mapper
public interface AnalyticsOutboxMapper {
  int insertIfAbsent(@Param("eventId") String eventId,
      @Param("applicationId") long applicationId,
      @Param("userId") Long userId,
      @Param("action") String action,
      @Param("occurredAtMs") long occurredAtMs,
      @Param("payload") String payload);

  AnalyticsOutboxEvent selectFact(@Param("eventId") String eventId);

  AnalyticsOutboxEvent selectFactForUpdate(@Param("eventId") String eventId);

  List<AnalyticsOutboxEvent> selectClaimable(@Param("limit") int limit);

  int claim(@Param("eventId") String eventId,
      @Param("leaseOwner") String leaseOwner,
      @Param("seconds") long seconds);

  int renewLease(@Param("eventId") String eventId,
      @Param("leaseOwner") String leaseOwner,
      @Param("seconds") long seconds);

  int markSent(@Param("eventId") String eventId, @Param("leaseOwner") String leaseOwner);

  int recordFailure(@Param("eventId") String eventId,
      @Param("leaseOwner") String leaseOwner,
      @Param("error") String error,
      @Param("delaySeconds") long delaySeconds,
      @Param("maxAttempts") int maxAttempts);

  int reject(@Param("eventId") String eventId,
      @Param("leaseOwner") String leaseOwner,
      @Param("error") String error);

  List<AnalyticsOutboxEvent> selectEvents(@Param("state") String state,
      @Param("limit") int limit,
      @Param("offset") int offset);

  int retryReview(@Param("eventId") String eventId);

  int requeueUnconsumed(@Param("seconds") long seconds,
      @Param("retentionCutoffMs") long retentionCutoffMs,
      @Param("limit") int limit,
      @Param("reason") String reason);

  long countPending();

  int deleteSentBefore(@Param("days") int days, @Param("limit") int limit);
}
