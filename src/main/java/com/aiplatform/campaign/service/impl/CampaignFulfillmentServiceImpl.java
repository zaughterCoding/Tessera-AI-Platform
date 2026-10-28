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
import com.aiplatform.campaign.service.ICampaignRedisService;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登记事务先提交预约身份，建单事务再消费数据库库存；两段都可独立恢复。 */
@Service
public class CampaignFulfillmentServiceImpl implements ICampaignFulfillmentService {
  private static final int REASON_LIMIT = 255;
  private static final String ADMITTED_STATE = "PROCESSING";
  private final CampaignReservationMapper reservations;
  private final LimitedCampaignMapper campaigns;
  private final BusinessOutboxMapper outbox;
  private final ICampaignOrderWriter writer;
  private final ICampaignRedisService redis;

  public CampaignFulfillmentServiceImpl(
      CampaignReservationMapper reservations,
      LimitedCampaignMapper campaigns,
      BusinessOutboxMapper outbox,
      ICampaignOrderWriter writer,
      ICampaignRedisService redis) {
    this.reservations = reservations;
    this.campaigns = campaigns;
    this.outbox = outbox;
    this.writer = writer;
    this.redis = redis;
  }

  @Override
  @Transactional
  public RegisterOutcome register(CampaignMessage message, boolean handoff) {
    LimitedCampaign campaign = campaigns.selectById(message.getCampaignId());
    if (campaign == null || campaign.getPublishedAt() == null) {
      return RegisterOutcome.INVALID;
    }
    CampaignReservation reservation =
        reservations.selectReservationForUpdate(message.getOrderId());
    if (reservation != null) {
      // 已有预约只能按数据库身份重投，消息字段不符时不得改动该预约。
      if (!sameIdentity(message, reservation)) {
        return RegisterOutcome.INVALID;
      }
      return continueReservation(message, campaign, reservation, handoff);
    }
    // 没有数据库预约时必须核验真实受理依据；消息本身不能自证身份。
    if (!admitted(message)) {
      return RegisterOutcome.INVALID;
    }
    reservations.insertProcessing(message.getOrderId(), message.getCampaignId(),
        message.getUserId(), message.getGeneration(), message.getRequestKey());
    reservation = reservations.selectReservationForUpdate(message.getOrderId());
    if (reservation == null || !sameIdentity(message, reservation)) {
      return RegisterOutcome.INVALID;
    }
    return continueReservation(message, campaign, reservation, handoff);
  }

  private RegisterOutcome continueReservation(CampaignMessage message, LimitedCampaign campaign,
      CampaignReservation reservation, boolean handoff) {
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

  private boolean sameIdentity(CampaignMessage message, CampaignReservation reservation) {
    return Objects.equals(message.getCampaignId(), reservation.getCampaignId())
        && Objects.equals(message.getUserId(), reservation.getUserId())
        && Objects.equals(message.getGeneration(), reservation.getGeneration())
        && Objects.equals(message.getRequestKey(), reservation.getRequestKey());
  }

  /** 受理脚本留下的订单、用户、活动、代次、请求键与资格必须与消息完全一致。 */
  private boolean admitted(CampaignMessage message) {
    if (message.getRequestKey() == null) {
      return false;
    }
    Map<String, String> state = redis.requestState(message.getOrderId());
    if (state.isEmpty()
        || !ADMITTED_STATE.equals(state.get("state"))
        || !sameNumber(state.get("orderId"), message.getOrderId())
        || !sameNumber(state.get("userId"), message.getUserId())
        || !sameNumber(state.get("campaignId"), message.getCampaignId())
        || !sameNumber(state.get("generation"), message.getGeneration())
        || !Objects.equals(state.get("requestKey"), message.getRequestKey())) {
      return false;
    }
    String orderId = String.valueOf(message.getOrderId());
    return orderId.equals(redis.existingOrderForUser(
            message.getCampaignId(), message.getUserId()))
        && orderId.equals(redis.existingOrderForRequestKey(
            message.getCampaignId(), message.getUserId(), message.getRequestKey()))
        && redis.isBuyer(message.getCampaignId(), message.getUserId());
  }

  private boolean sameNumber(String value, long expected) {
    if (value == null) {
      return false;
    }
    try {
      return Long.parseLong(value) == expected;
    } catch (NumberFormatException failure) {
      return false;
    }
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
