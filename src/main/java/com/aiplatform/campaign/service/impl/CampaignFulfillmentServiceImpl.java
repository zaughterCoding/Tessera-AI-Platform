package com.aiplatform.campaign.service.impl;

import com.aiplatform.campaign.CampaignMessage;
import com.aiplatform.campaign.RegisterOutcome;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignFulfillmentService;
import com.aiplatform.campaign.service.ICampaignOrderWriter;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登记事务先提交预约身份，建单事务再消费数据库库存；两段都可独立恢复。 */
@Service
public class CampaignFulfillmentServiceImpl implements ICampaignFulfillmentService {
  private static final int REASON_LIMIT = 255;
  private final CampaignReservationMapper reservations;
  private final LimitedCampaignMapper campaigns;
  private final BusinessOutboxMapper outbox;
  private final ICampaignOrderWriter writer;

  public CampaignFulfillmentServiceImpl(
      CampaignReservationMapper reservations,
      LimitedCampaignMapper campaigns,
      BusinessOutboxMapper outbox,
      ICampaignOrderWriter writer) {
    this.reservations = reservations;
    this.campaigns = campaigns;
    this.outbox = outbox;
    this.writer = writer;
  }

  @Override
  @Transactional
  public RegisterOutcome register(CampaignMessage message, boolean handoff) {
    LimitedCampaign campaign = campaigns.selectById(message.getCampaignId());
    if (campaign == null || campaign.getPublishedAt() == null) {
      return RegisterOutcome.INVALID;
    }
    reservations.insertProcessing(message.getOrderId(), message.getCampaignId(),
        message.getUserId(), message.getGeneration(), message.getRequestKey());
    CampaignReservation reservation =
        reservations.selectReservationForUpdate(message.getOrderId());
    if (reservation == null) {
      return RegisterOutcome.INVALID;
    }
    if (!Objects.equals(message.getCampaignId(), reservation.getCampaignId())
        || !Objects.equals(message.getUserId(), reservation.getUserId())
        || !Objects.equals(message.getGeneration(), reservation.getGeneration())) {
      return RegisterOutcome.INVALID;
    }
    if (ReservationState.SUCCESS.name().equals(reservation.getState())) {
      return RegisterOutcome.SUCCESS;
    }
    if (ReservationState.FAILED.name().equals(reservation.getState())) {
      return RegisterOutcome.FAILED;
    }
    if (ReservationState.REVIEW.name().equals(reservation.getState())) {
      return RegisterOutcome.REVIEW;
    }
    if (!Objects.equals(message.getGeneration(), campaign.getGeneration())) {
      reservations.markReview(message.getOrderId(), "预约消息代次已过期");
      return RegisterOutcome.STALE;
    }
    if (handoff) {
      outbox.insertCampaignFulfillment(message.getOrderId(), message.getUserId(),
          "campaign-fulfill:" + message.getOrderId());
    }
    return RegisterOutcome.PROCESSING;
  }

  @Override
  public void fulfill(long orderId) {
    writer.fulfill(orderId);
  }

  @Override
  @Transactional
  public int recordFailure(long orderId, Throwable failure) {
    int updated = reservations.recordAttempt(orderId, summarize(failure));
    if (updated == 0) {
      return 0;
    }
    CampaignReservation reservation = reservations.selectReservation(orderId);
    return reservation == null || reservation.getAttempts() == null
        ? 0 : reservation.getAttempts();
  }

  @Override
  @Transactional
  public void markReview(long orderId, String reason) {
    reservations.markReview(orderId, summarize(reason));
  }

  private String summarize(Throwable failure) {
    String message = failure.getMessage();
    if (message == null || message.isEmpty()) {
      message = failure.getClass().getSimpleName();
    }
    return summarize(message);
  }

  private String summarize(String reason) {
    if (reason == null) {
      return "unknown";
    }
    return reason.length() <= REASON_LIMIT ? reason : reason.substring(0, REASON_LIMIT);
  }
}
