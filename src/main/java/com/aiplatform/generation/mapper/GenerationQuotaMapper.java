package com.aiplatform.generation.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户活动任务上限只使用稳定行与条件更新，不先查后插。 */
@Mapper
public interface GenerationQuotaMapper {
  int ensureRow(@Param("userId") long userId);

  /** 用户未单独配置时使用传入的全局上限；判断和占用在同一条更新内完成。 */
  int tryReserve(@Param("userId") long userId, @Param("limit") int limit);

  int release(@Param("userId") long userId);

  Integer selectActive(@Param("userId") long userId);

  SchedulingRow selectScheduling(@Param("userId") long userId);

  /** 只改配置，不动活动任务数；调低上限不影响已受理的任务。 */
  int upsertScheduling(@Param("userId") long userId, @Param("activeLimit") Integer activeLimit,
      @Param("scheduleWeight") Integer scheduleWeight);

  /** 用户调度配置行；配置列为空表示跟随全局默认值。 */
  @Data
  class SchedulingRow {
    private int activeTasks;
    private Integer activeLimit;
    private Integer scheduleWeight;
  }
}
