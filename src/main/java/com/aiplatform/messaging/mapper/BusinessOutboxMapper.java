package com.aiplatform.messaging.mapper;

import com.aiplatform.messaging.dto.OrderTaskDTO;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessOutboxMapper {
  int insertEvent(@Param("kind") String kind,
      @Param("aggregateId") long aggregateId,
      @Param("userId") Long userId);

  int insertEventAt(@Param("kind") String kind,
      @Param("aggregateId") long aggregateId,
      @Param("userId") Long userId,
      @Param("deliverAt") LocalDateTime deliverAt);

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

  int markConsumed(@Param("id") long id, @Param("markSent") boolean markSent);

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

  int insertCampaignFulfillment(@Param("orderId") long orderId,
      @Param("userId") long userId,
      @Param("idempotencyKey") String idempotencyKey);

  /** 粉丝分批未完成时，以已处理到的粉丝边 id 建立续传任务。 */
  int insertFeedContinuation(@Param("creationId") long creationId,
      @Param("userId") long userId,
      @Param("cursorId") long cursorId);

  BusinessOutboxTask selectCampaignFulfillment(@Param("orderId") long orderId);

  int rearmCampaignFulfillment(@Param("orderId") long orderId);

  int requeueCampaignFulfillment(@Param("orderId") long orderId);

  int cancelCampaignFulfillment(@Param("orderId") long orderId);

  long countPendingCampaignEvents(@Param("campaignId") long campaignId);

  long countPendingCampaignHandoffs();

  long countDoneBefore(@Param("cutoff") LocalDateTime cutoff);

  int deleteDoneBefore(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

  /** 最早已到期但仍未投递的任务时间；无积压时为 null。 */
  LocalDateTime oldestPendingReadyAt();

  List<OrderTaskDTO> selectOrderTasks(@Param("orderId") long orderId);
}
