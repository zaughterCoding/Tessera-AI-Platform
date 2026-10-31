package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.ProviderAttempt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProviderAttemptMapper extends BaseMapper<ProviderAttempt> {
  int insertAttempt(@Param("attempt") ProviderAttempt attempt,
      @Param("leaseSeconds") int leaseSeconds);

  ProviderAttempt selectByTaskId(@Param("taskId") long taskId);

  List<ProviderAttempt> selectByTaskIds(@Param("taskIds") List<Long> taskIds);

  /** 回调按客户端尝试身份定位，先于提交响应到达也能收敛。 */
  ProviderAttempt selectByAttemptKey(@Param("attemptKey") String attemptKey);

  ProviderAttempt selectByAttemptKeyForUpdate(@Param("attemptKey") String attemptKey);

  /** 到期查询包含人工复核行，慢速轮询仍可发现晚到的终态事实。 */
  List<ProviderAttempt> selectDueForPoll(@Param("limit") int limit);

  int claimForPoll(@Param("id") long id, @Param("owner") String owner,
      @Param("leaseSeconds") int leaseSeconds);

  int confirmSubmit(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("providerTaskId") String providerTaskId,
      @Param("state") String state, @Param("pollSeconds") int pollSeconds);

  int markUncertain(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("delaySeconds") int delaySeconds,
      @Param("code") String code, @Param("detail") String detail);

  int applyActive(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("state") String state,
      @Param("providerTaskId") String providerTaskId,
      @Param("nextSeconds") int nextSeconds);

  int applyTerminalWithLease(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("state") String state,
      @Param("providerTaskId") String providerTaskId, @Param("resultRef") String resultRef,
      @Param("resultDigest") String resultDigest, @Param("failureCode") String failureCode,
      @Param("failureDetail") String failureDetail);

  /** 查询确认上游不存在原尝试时，用同一 attemptKey 安全重提，并累计提交次数。 */
  int recordResubmit(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("providerTaskId") String providerTaskId,
      @Param("state") String state, @Param("nextSeconds") int nextSeconds);

  int recordQueryFailure(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("exhausted") boolean exhausted,
      @Param("delaySeconds") int delaySeconds, @Param("error") String error);

  int markReview(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("error") String error,
      @Param("nextSeconds") int nextSeconds);

  int applyCallbackAccepted(@Param("id") long id, @Param("providerTaskId") String providerTaskId,
      @Param("nextSeconds") int nextSeconds);

  int applyCallbackRunning(@Param("id") long id, @Param("providerTaskId") String providerTaskId,
      @Param("nextSeconds") int nextSeconds);

  int applyCallbackTerminal(@Param("id") long id, @Param("state") String state,
      @Param("providerTaskId") String providerTaskId, @Param("resultRef") String resultRef,
      @Param("resultDigest") String resultDigest, @Param("failureCode") String failureCode,
      @Param("failureDetail") String failureDetail);

  /** 上游确认取消时推进为终态；已存在结果或失败事实时拒绝覆盖。 */
  int confirmUpstreamCancelled(@Param("id") long id);

  /** 历史成功快照缺失结果元数据时只补字段，不改变终态与容量。 */
  int completePendingResult(@Param("id") long id, @Param("providerTaskId") String providerTaskId,
      @Param("resultRef") String resultRef, @Param("resultDigest") String resultDigest);

  /** 查询补全使用同一 owner + generation 围栏，避免覆盖接管后的尝试。 */
  int completePendingResultWithLease(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("providerTaskId") String providerTaskId,
      @Param("resultRef") String resultRef, @Param("resultDigest") String resultDigest);

  /** 上游仍无法给出完整结果时只延后补查，不改变终态、不释放容量。 */
  int delayPendingResultRepair(@Param("id") long id, @Param("owner") String owner,
      @Param("generation") long generation, @Param("delaySeconds") int delaySeconds,
      @Param("detail") String detail);

  int markReconcileNow(@Param("id") long id);

  int countByState(@Param("state") String state);

  List<ProviderAttempt> selectRecent(@Param("limit") int limit);
}
