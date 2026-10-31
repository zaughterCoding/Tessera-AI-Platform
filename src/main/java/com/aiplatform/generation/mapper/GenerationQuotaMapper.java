package com.aiplatform.generation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户活动任务上限只使用稳定行与条件更新，不先查后插。 */
@Mapper
public interface GenerationQuotaMapper {
  int ensureRow(@Param("userId") long userId);

  int tryReserve(@Param("userId") long userId, @Param("limit") int limit);

  int release(@Param("userId") long userId);

  Integer selectActive(@Param("userId") long userId);
}
