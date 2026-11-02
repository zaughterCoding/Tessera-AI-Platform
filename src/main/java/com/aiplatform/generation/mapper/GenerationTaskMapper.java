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

  int updateNote(@Param("taskId") long taskId, @Param("note") String note);

  int updateStateUnlessFinal(@Param("taskId") long taskId, @Param("state") String state,
      @Param("note") String note);

  /** 结果保存重试耗尽：仅把仍冻结的待交付任务转人工核对，不改账务。 */
  int markReviewAfterSaveFailure(@Param("taskId") long taskId, @Param("note") String note);

  GenerationTask selectByIdForUpdate(@Param("id") long id);

  /** 任务账务终态只允许从冻结条件更新；唯一键与状态条件共同阻止双结算。 */
  int finish(@Param("taskId") long taskId, @Param("state") String state,
      @Param("accounting") String accounting, @Param("note") String note,
      @Param("states") List<String> states);

  /** 取消意图先落库并把未终结任务标为 CANCEL_REQUESTED；已有上游成功事实时不动。 */
  int markCancelRequested(@Param("taskId") long taskId, @Param("note") String note);

  /** 渠道不支持取消时恢复原执行状态，取消请求单独标记为不支持。 */
  int restoreFromCancel(@Param("taskId") long taskId, @Param("state") String state,
      @Param("note") String note);

  /** 结算恢复扫描只返回仍冻结且可能已有最终决定的候选任务。 */
  List<GenerationTask> selectSettlementCandidates(@Param("limit") int limit);

  long countByAccounting(@Param("accounting") String accounting);

  int countByState(@Param("state") String state);

  /** 管理查询按状态过滤；只读，不参与调度或账务。 */
  List<GenerationTask> selectRecentTasks(@Param("state") String state,
      @Param("limit") int limit, @Param("offset") int offset);
}
