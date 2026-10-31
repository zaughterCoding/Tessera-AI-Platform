package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.GenerationRequest;
import com.aiplatform.generation.entity.GenerationTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTask> {
  /** 唯一键竞争而不是先查后插；失败时保持既有请求行不变。 */
  int insertRequestIfAbsent(@Param("userId") long userId,
      @Param("requestKey") String requestKey,
      @Param("requestDigest") String requestDigest);

  GenerationRequest selectRequestForUpdate(@Param("userId") long userId,
      @Param("requestKey") String requestKey);

  int bindRequestTask(@Param("requestId") long requestId, @Param("taskId") long taskId);

  int insertTask(@Param("task") GenerationTask task);

  int insertTaskMedia(@Param("taskId") long taskId,
      @Param("mediaIds") List<Long> mediaIds);

  int insertHandoff(@Param("taskId") long taskId);

  List<Long> selectMediaIds(@Param("taskId") long taskId);

  int updateStateIn(@Param("taskId") long taskId, @Param("state") String state,
      @Param("note") String note, @Param("states") List<String> states);

  int updateStateUnlessFinal(@Param("taskId") long taskId, @Param("state") String state,
      @Param("note") String note);

  int countByState(@Param("state") String state);

  /** 管理查询按状态过滤；只读，不参与调度或账务。 */
  List<GenerationTask> selectRecentTasks(@Param("state") String state,
      @Param("limit") int limit, @Param("offset") int offset);
}
