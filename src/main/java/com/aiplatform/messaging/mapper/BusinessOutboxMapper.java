package com.aiplatform.messaging.mapper;

import com.aiplatform.messaging.entity.BusinessOutboxTask;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessOutboxMapper {
  int insertEvent(@Param("kind") String kind,
      @Param("aggregateId") long aggregateId,
      @Param("userId") Long userId);

  BusinessOutboxTask selectEvent(@Param("id") long id);

  BusinessOutboxTask selectEventForUpdate(@Param("id") long id);

  List<BusinessOutboxTask> selectClaimableEvents(@Param("limit") int limit);

  int claimEvent(@Param("id") long id,
      @Param("leaseOwner") String leaseOwner,
      @Param("seconds") long seconds);

  List<BusinessOutboxTask> selectEvents(@Param("state") String state,
      @Param("limit") int limit,
      @Param("offset") int offset);

  int renewLease(@Param("id") long id,
      @Param("leaseOwner") String leaseOwner,
      @Param("seconds") long seconds);

  int markSent(@Param("id") long id, @Param("leaseOwner") String leaseOwner);

  int markConsumed(@Param("id") long id);

  int markDuplicateDone(@Param("id") long id);

  int deferDeadline(@Param("id") long id, @Param("leaseOwner") String leaseOwner);

  int recordSendFailure(@Param("id") long id,
      @Param("leaseOwner") String leaseOwner,
      @Param("error") String error,
      @Param("delaySeconds") long delaySeconds,
      @Param("maxAttempts") int maxAttempts);

  int recordConsumeFailure(@Param("id") long id, @Param("error") String error);

  int markReview(@Param("id") long id);

  int redriveSentEvents(@Param("ageSeconds") long ageSeconds);

  int redriveFailedEvents(@Param("ageSeconds") long ageSeconds);

  int retryReview(@Param("id") long id);

  long countByState(@Param("state") String state);
}
