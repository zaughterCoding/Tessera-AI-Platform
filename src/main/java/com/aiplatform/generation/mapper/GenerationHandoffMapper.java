package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.GenerationHandoff;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GenerationHandoffMapper extends BaseMapper<GenerationHandoff> {
  /** 到期交接与过期认领一并返回；调用方必须处在事务内并持有行锁。 */
  List<HandoffRow> selectDueHandoffs(@Param("limit") int limit);

  int claimHandoff(@Param("id") long id, @Param("owner") String owner,
      @Param("leaseSeconds") int leaseSeconds);

  int completeClaimedHandoff(@Param("id") long id, @Param("generation") long generation,
      @Param("reason") String reason);

  int completeWaitingHandoff(@Param("id") long id, @Param("state") String state,
      @Param("reason") String reason);

  int markRetry(@Param("id") long id, @Param("state") String state,
      @Param("delaySeconds") int delaySeconds, @Param("error") String error);

  /** 只修复过期认领的租约，不改变已有尝试。 */
  int clearExpiredClaim(@Param("id") long id, @Param("generation") long generation);

  /** 后台安全恢复只把等待行提前到当前时间，不修改任务、账务或渠道计数。 */
  int wakeWaiting(@Param("id") long id);

  int countByState(@Param("state") String state);

  /** 交接行与任务状态的只读视图；不持有锁时使用。 */
  List<HandoffRow> selectByTaskId(@Param("taskId") long taskId);

  /** 调度到期视图，供后台只读查询。 */
  List<HandoffRow> selectRecent(@Param("limit") int limit);

  /** 行视图只包含调度需要的可信字段。 */
  class HandoffRow {
    private Long handoffId;
    private Long taskId;
    private Long userId;
    private Long applicationId;
    private String handoffState;
    private String taskState;
    private Long generation;
    private Integer attempts;
    private String claimOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime enqueuedAt;
    private LocalDateTime nextAttemptAt;
    private String lastError;

    public Long getHandoffId() {
      return handoffId;
    }

    public void setHandoffId(Long handoffId) {
      this.handoffId = handoffId;
    }

    public Long getTaskId() {
      return taskId;
    }

    public void setTaskId(Long taskId) {
      this.taskId = taskId;
    }

    public Long getUserId() {
      return userId;
    }

    public void setUserId(Long userId) {
      this.userId = userId;
    }

    public Long getApplicationId() {
      return applicationId;
    }

    public void setApplicationId(Long applicationId) {
      this.applicationId = applicationId;
    }

    public String getHandoffState() {
      return handoffState;
    }

    public void setHandoffState(String handoffState) {
      this.handoffState = handoffState;
    }

    public String getTaskState() {
      return taskState;
    }

    public void setTaskState(String taskState) {
      this.taskState = taskState;
    }

    public Long getGeneration() {
      return generation;
    }

    public void setGeneration(Long generation) {
      this.generation = generation;
    }

    public Integer getAttempts() {
      return attempts;
    }

    public void setAttempts(Integer attempts) {
      this.attempts = attempts;
    }

    public String getClaimOwner() {
      return claimOwner;
    }

    public void setClaimOwner(String claimOwner) {
      this.claimOwner = claimOwner;
    }

    public LocalDateTime getLeaseUntil() {
      return leaseUntil;
    }

    public void setLeaseUntil(LocalDateTime leaseUntil) {
      this.leaseUntil = leaseUntil;
    }

    public LocalDateTime getEnqueuedAt() {
      return enqueuedAt;
    }

    public void setEnqueuedAt(LocalDateTime enqueuedAt) {
      this.enqueuedAt = enqueuedAt;
    }

    public LocalDateTime getNextAttemptAt() {
      return nextAttemptAt;
    }

    public void setNextAttemptAt(LocalDateTime nextAttemptAt) {
      this.nextAttemptAt = nextAttemptAt;
    }

    public String getLastError() {
      return lastError;
    }

    public void setLastError(String lastError) {
      this.lastError = lastError;
    }
  }
}
