package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.GenerationResult;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationResultMapper {
  int insertIgnore(@Param("taskId") long taskId, @Param("attemptId") long attemptId,
      @Param("position") int position, @Param("sourceRef") String sourceRef,
      @Param("expectedDigest") String expectedDigest);

  GenerationResult selectById(@Param("id") long id);

  List<GenerationResult> selectByTaskId(@Param("taskId") long taskId);

  List<GenerationResult> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

  long countByTaskId(@Param("taskId") long taskId);

  long countByTaskState(@Param("taskId") long taskId, @Param("state") String state);

  long countByState(@Param("state") String state);

  /** 到期或租约过期的结果由单个工作者认领；网络与文件操作在事务提交后进行。 */
  List<ResultClaimRow> selectDue(@Param("limit") int limit);

  int claim(@Param("id") long id, @Param("owner") String owner,
      @Param("leaseSeconds") int leaseSeconds);

  int markSaved(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("mediaId") long mediaId,
      @Param("contentDigest") String contentDigest, @Param("contentType") String contentType,
      @Param("sizeBytes") long sizeBytes);

  int fail(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("finalFailure") boolean finalFailure,
      @Param("error") String error, @Param("delaySeconds") int delaySeconds);

  int markDelivered(@Param("taskId") long taskId);

  /** 管理重试只重置待保存事实，仍复用原结果身份与原摘要，不改变任务或余额。 */
  int resetFailed(@Param("taskId") long taskId);

  List<GenerationResult> selectRecent(@Param("state") String state,
      @Param("limit") int limit, @Param("offset") int offset);

  long countUnreferencedGeneratedMedia(@Param("graceSeconds") long graceSeconds);

  List<Long> selectUnreferencedGeneratedMedia(@Param("graceSeconds") long graceSeconds,
      @Param("limit") int limit);

  class ResultClaimRow extends GenerationResult {
    private Long userId;
    private String taskState;
    private Integer taskOutputCount;

    public Long getUserId() {
      return userId;
    }

    public void setUserId(Long userId) {
      this.userId = userId;
    }

    public String getTaskState() {
      return taskState;
    }

    public void setTaskState(String taskState) {
      this.taskState = taskState;
    }

    public Integer getTaskOutputCount() {
      return taskOutputCount;
    }

    public void setTaskOutputCount(Integer taskOutputCount) {
      this.taskOutputCount = taskOutputCount;
    }
  }
}
