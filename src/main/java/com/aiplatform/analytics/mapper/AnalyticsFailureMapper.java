package com.aiplatform.analytics.mapper;

import com.aiplatform.analytics.entity.AnalyticsFailureRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 异常消息隔离与复核视图；失败记录不参与任何计数。 */
@Mapper
public interface AnalyticsFailureMapper {
  int insertFailure(@Param("topic") String topic,
      @Param("partitionId") int partitionId,
      @Param("messageOffset") long messageOffset,
      @Param("payload") String payload,
      @Param("reason") String reason);

  List<AnalyticsFailureRecord> listFailures(@Param("limit") int limit,
      @Param("offset") int offset);

  long countFailures();

  long countFailuresBefore(@Param("cutoff") LocalDateTime cutoff);

  int deleteFailuresBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
