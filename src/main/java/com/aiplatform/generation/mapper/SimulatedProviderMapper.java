package com.aiplatform.generation.mapper;

import com.aiplatform.generation.entity.SimulatedProviderFault;
import com.aiplatform.generation.entity.SimulatedProviderJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 模拟上游独立存储；事务边界不依赖调用方的业务事务。 */
@Mapper
public interface SimulatedProviderMapper {
  SimulatedProviderFault selectFaultForUpdate(@Param("attemptKey") String attemptKey,
      @Param("userId") Long userId, @Param("applicationId") Long applicationId);

  int deleteFault(@Param("id") long id);

  int decrementFault(@Param("id") long id);

  int insertFault(@Param("attemptKey") String attemptKey, @Param("userId") Long userId,
      @Param("applicationId") Long applicationId, @Param("mode") String mode,
      @Param("durationMs") Integer durationMs, @Param("remaining") int remaining);

  int insertJobIgnore(@Param("attemptKey") String attemptKey,
      @Param("providerTaskId") String providerTaskId, @Param("state") String state,
      @Param("faultMode") String faultMode, @Param("durationMs") int durationMs,
      @Param("queryFailures") int queryFailures);

  int insertJob(@Param("attemptKey") String attemptKey,
      @Param("providerTaskId") String providerTaskId, @Param("state") String state,
      @Param("faultMode") String faultMode, @Param("durationMs") int durationMs,
      @Param("queryFailures") int queryFailures);

  SimulatedProviderJob selectJob(@Param("attemptKey") String attemptKey);

  SimulatedProviderJob selectJobForUpdate(@Param("attemptKey") String attemptKey);

  int completeJob(@Param("id") long id, @Param("state") String state,
      @Param("resultRef") String resultRef, @Param("resultDigest") String resultDigest,
      @Param("failureCode") String failureCode, @Param("failureDetail") String failureDetail);

  int decrementQueryFailures(@Param("id") long id);

  int cancelJob(@Param("id") long id);

  int countJobs();
}
