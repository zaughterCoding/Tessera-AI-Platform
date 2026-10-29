package com.aiplatform.analytics.mapper;

import com.aiplatform.analytics.entity.AnalyticsEventRecord;
import com.aiplatform.analytics.entity.AnalyticsOverviewRow;
import com.aiplatform.analytics.entity.PopularityRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 明细去重、分钟桶与窗口查询；所有写入由上层事务统一提交。 */
@Mapper
public interface AnalyticsEventMapper {
  int insertEvent(@Param("eventId") String eventId,
      @Param("applicationId") long applicationId,
      @Param("userId") Long userId,
      @Param("action") String action,
      @Param("occurredAtMs") long occurredAtMs,
      @Param("bucketMs") long bucketMs);

  AnalyticsEventRecord selectEventForUpdate(@Param("eventId") String eventId);

  int upsertBucket(@Param("applicationId") long applicationId,
      @Param("bucketMs") long bucketMs,
      @Param("action") String action);

  int insertVisitUser(@Param("applicationId") long applicationId,
      @Param("userId") long userId,
      @Param("bucketMs") long bucketMs);

  List<PopularityRow> selectPopularity(@Param("fromMs") long fromMs,
      @Param("toMs") long toMs,
      @Param("visitWeight") int visitWeight,
      @Param("uvWeight") int uvWeight,
      @Param("orderWeight") int orderWeight,
      @Param("paymentWeight") int paymentWeight,
      @Param("claimWeight") int claimWeight,
      @Param("limit") int limit);

  AnalyticsOverviewRow selectBucketTotals(@Param("applicationId") long applicationId,
      @Param("fromMs") long fromMs,
      @Param("toMs") long toMs);

  long selectUv(@Param("applicationId") long applicationId,
      @Param("fromMs") long fromMs,
      @Param("toMs") long toMs);

  String selectApplicationName(@Param("applicationId") long applicationId);

  int deleteEventsBefore(@Param("cutoffMs") long cutoffMs, @Param("limit") int limit);

  int deleteVisitUsersBefore(@Param("cutoffMs") long cutoffMs, @Param("limit") int limit);

  int deleteBucketsBefore(@Param("cutoffMs") long cutoffMs, @Param("limit") int limit);
}
