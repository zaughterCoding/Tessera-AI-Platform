package com.aiplatform.campaign.mapper;

import com.aiplatform.campaign.entity.CampaignReservation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CampaignReservationMapper {
  int insertProcessing(@Param("orderId") long orderId,
      @Param("campaignId") long campaignId,
      @Param("userId") long userId,
      @Param("generation") int generation,
      @Param("requestKey") String requestKey);

  CampaignReservation selectReservation(@Param("orderId") long orderId);

  CampaignReservation selectReservationForUpdate(@Param("orderId") long orderId);

  CampaignReservation selectByRequest(@Param("campaignId") long campaignId,
      @Param("userId") long userId,
      @Param("requestKey") String requestKey);

  int markSuccess(@Param("orderId") long orderId);

  int markReview(@Param("orderId") long orderId, @Param("reason") String reason);

  int markFailed(@Param("orderId") long orderId, @Param("reason") String reason);

  int resetForRetry(@Param("orderId") long orderId);

  int recordAttempt(@Param("orderId") long orderId, @Param("reason") String reason);

  int markStockReleased(@Param("orderId") long orderId);

  long countByState(@Param("campaignId") long campaignId, @Param("state") String state);

  long countAllByState(@Param("state") String state);

  long countProcessingOrReview(@Param("campaignId") Long campaignId);

  long countUnreleasedSuccessWithOrder(@Param("campaignId") long campaignId);

  List<CampaignReservation> selectActiveSuccess(@Param("campaignId") long campaignId);

  List<CampaignReservation> selectReview(@Param("campaignId") long campaignId,
      @Param("limit") int limit);
}
