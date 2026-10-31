package com.aiplatform.entitlement.mapper;

import com.aiplatform.entitlement.entity.GenerationSettlement;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationSettlementMapper {
  int insert(GenerationSettlement settlement);

  GenerationSettlement selectByTaskId(@Param("taskId") long taskId);

  List<GenerationSettlement> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

  List<GenerationSettlement> selectRecent(@Param("limit") int limit,
      @Param("offset") int offset);

  long countByKind(@Param("kind") String kind);

  long countAll();
}
