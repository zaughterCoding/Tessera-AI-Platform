package com.aiplatform.analytics.mapper;

import com.aiplatform.analytics.entity.AnalyticsBackfillEvent;
import com.aiplatform.analytics.entity.AnalyticsBackfillState;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 历史补数状态与订单事件历史游标；批次处理与游标推进同事务。 */
@Mapper
public interface AnalyticsBackfillMapper {
  AnalyticsBackfillState selectStateForUpdate(@Param("id") int id);

  AnalyticsBackfillState selectState(@Param("id") int id);

  int start(@Param("id") int id,
      @Param("cursor") long cursor,
      @Param("rangeFromMs") long rangeFromMs);

  int restart(@Param("id") int id, @Param("rangeFromMs") long rangeFromMs);

  int advance(@Param("id") int id,
      @Param("cursor") long cursor,
      @Param("count") int count);

  int complete(@Param("id") int id,
      @Param("cursor") long cursor,
      @Param("count") int count);

  int markFailed(@Param("id") int id, @Param("error") String error);

  List<AnalyticsBackfillEvent> selectEvents(@Param("cursor") long cursor,
      @Param("rangeFromSeconds") long rangeFromSeconds,
      @Param("limit") int limit);
}
