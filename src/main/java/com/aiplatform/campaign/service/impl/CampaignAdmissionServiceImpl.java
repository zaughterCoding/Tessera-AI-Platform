package com.aiplatform.campaign.service.impl;

import com.aiplatform.cache.ILock;
import com.aiplatform.cache.SimpleRedisLock;
import com.aiplatform.campaign.RedisIdWorker;
import com.aiplatform.campaign.dto.CampaignAdmissionDTO;
import com.aiplatform.campaign.dto.CampaignAdmissionForm;
import com.aiplatform.campaign.dto.CampaignOfferDTO;
import com.aiplatform.campaign.dto.CampaignReservationDTO;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.enums.CampaignState;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignAdmissionService;
import com.aiplatform.campaign.service.ICampaignRedisService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.trade.dto.PurchaseOrderDTO;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 用户级锁与 Redis 原子脚本共同判定入口；数据库只用于可信身份和最终订单。 */
@Slf4j
@Service
public class CampaignAdmissionServiceImpl implements ICampaignAdmissionService {
  private static final int LOCK_SECONDS = 5;
  private static final String ENTITLEMENT_NOTICE = "权益使用功能尚未开放";
  private final LimitedCampaignMapper campaigns;
  private final CampaignReservationMapper reservations;
  private final PurchaseOrderMapper orders;
  private final ICampaignRedisService redis;
  private final RedisIdWorker idWorker;
  private final StringRedisTemplate redisTemplate;
  private final String namespace;
  private final boolean consumerEnabled;

  public CampaignAdmissionServiceImpl(
      LimitedCampaignMapper campaigns,
      CampaignReservationMapper reservations,
      PurchaseOrderMapper orders,
      ICampaignRedisService redis,
      RedisIdWorker idWorker,
      StringRedisTemplate redisTemplate,
      @Value("${app.namespace}") String namespace,
      @Value("${app.campaign.consumer-enabled:true}") boolean consumerEnabled) {
    this.campaigns = campaigns;
    this.reservations = reservations;
    this.orders = orders;
    this.redis = redis;
    this.idWorker = idWorker;
    this.redisTemplate = redisTemplate;
    this.namespace = namespace;
    this.consumerEnabled = consumerEnabled;
  }

  @Override
  public CampaignAdmissionDTO admit(long userId, CampaignAdmissionForm form) {
    if (!consumerEnabled) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    long campaignId = form.getCampaignId();
    LimitedCampaign campaign = campaigns.selectById(campaignId);
    if (campaign == null || campaign.getPublishedAt() == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    requireJoinable(campaign);
    String requestKey = form.getRequestKey().trim();
    ILock lock = new SimpleRedisLock(
        "campaign:admit:" + campaignId + ":" + userId, redisTemplate, namespace);
    if (!lock.tryLock(LOCK_SECONDS)) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    try {
      long orderId = idWorker.nextOrderId();
      long result;
      try {
        result = redis.admit(orderId, userId, campaignId, requestKey);
      } catch (RuntimeException failure) {
        log.warn("活动入口判定失败，campaignId={}，type={}",
            campaignId, failure.getClass().getSimpleName());
        throw new ApiException(ErrorCode.UNAVAILABLE);
      }
      return interpret(userId, campaign, requestKey, orderId, result);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public CampaignReservationDTO query(long userId, long orderId) {
    CampaignReservation reservation = reservations.selectReservation(orderId);
    if (reservation != null) {
      if (!Long.valueOf(userId).equals(reservation.getUserId())) {
        throw new ApiException(ErrorCode.NOT_FOUND);
      }
      return build(reservation);
    }
    Map<String, String> state = redis.requestState(orderId);
    if (state.isEmpty() || !sameUser(state.get("userId"), userId)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return buildRedis(orderId, state);
  }

  @Override
  public CampaignReservationDTO queryByRequest(
      long userId, long campaignId, String requestKey) {
    CampaignReservation reservation =
        reservations.selectByRequest(campaignId, userId, requestKey);
    if (reservation != null) {
      return build(reservation);
    }
    String orderId = redis.existingOrderForRequestKey(campaignId, userId, requestKey);
    if (orderId == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    try {
      return query(userId, Long.parseLong(orderId));
    } catch (NumberFormatException failure) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
  }

  @Override
  public List<CampaignOfferDTO> offers() {
    List<CampaignOfferDTO> offers = campaigns.selectPublicOffers();
    for (CampaignOfferDTO offer : offers) {
      decorate(offer);
    }
    return offers;
  }

  @Override
  public CampaignOfferDTO offer(long id) {
    CampaignOfferDTO offer = campaigns.selectPublicOffer(id);
    if (offer == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    decorate(offer);
    return offer;
  }

  private void requireJoinable(LimitedCampaign campaign) {
    if (!CampaignState.ACTIVE.name().equals(campaign.getPublishState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    if (now.isBefore(campaign.getBeginAt()) || now.isAfter(campaign.getEndAt())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
  }

  private CampaignAdmissionDTO interpret(long userId, LimitedCampaign campaign,
      String requestKey, long orderId, long result) {
    if (result == 1L) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (result == 3L) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (result == 4L || result == 5L || result < 0) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    if (result == 2L) {
      String existing = redis.existingOrderForUser(campaign.getId(), userId);
      if (existing == null) {
        throw new ApiException(ErrorCode.CONFLICT);
      }
      return fromRedis(userId, campaign.getId(), requestKey, existing, true);
    }
    if (result == 6L) {
      String existing = redis.existingOrderForRequestKey(campaign.getId(), userId, requestKey);
      if (existing == null) {
        throw new ApiException(ErrorCode.CONFLICT);
      }
      return fromRedis(userId, campaign.getId(), requestKey, existing, true);
    }
    CampaignAdmissionDTO accepted = new CampaignAdmissionDTO();
    accepted.setOrderId(orderId);
    accepted.setCampaignId(campaign.getId());
    accepted.setGeneration(campaign.getGeneration());
    accepted.setState(ReservationState.PROCESSING.name());
    accepted.setRequestKey(requestKey);
    accepted.setExisting(false);
    accepted.setAcceptanceNotice("已受理预购请求，订单正在创建；受理不代表建单成功或已付款");
    accepted.setReservationNotice("已受理，订单正在创建；不代表建单成功或已付款");
    accepted.setEntitlementNotice(ENTITLEMENT_NOTICE);
    return accepted;
  }

  private CampaignAdmissionDTO fromRedis(long userId, long campaignId, String requestKey,
      String existingId, boolean existing) {
    long orderId;
    try {
      orderId = Long.parseLong(existingId);
    } catch (NumberFormatException failure) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    Map<String, String> state = redis.requestState(orderId);
    if (!state.isEmpty() && !sameUser(state.get("userId"), userId)) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    String originalKey = state.get("requestKey");
    CampaignAdmissionDTO view = new CampaignAdmissionDTO();
    view.setOrderId(orderId);
    view.setCampaignId(campaignId);
    view.setGeneration(number(state.get("generation")));
    view.setState(displayState(state.get("state")));
    // 重复请求返回原预约时展示原请求键，避免用新键稍后查询不到。
    view.setRequestKey(originalKey == null || originalKey.isEmpty() ? requestKey : originalKey);
    view.setExisting(existing);
    view.setAcceptanceNotice("已受理（重复请求返回原预约），不代表建单成功或已付款");
    view.setReservationNotice(stateNotice(view.getState()));
    view.setEntitlementNotice(ENTITLEMENT_NOTICE);
    return view;
  }

  private CampaignReservationDTO build(CampaignReservation reservation) {
    CampaignReservationDTO view = base(reservation);
    PurchaseOrder order = orders.selectById(reservation.getOrderId());
    if (order == null) {
      view.setReservationNotice(reservationNotice(reservation.getState(), null));
      if (ReservationState.SUCCESS.name().equals(reservation.getState())) {
        view.setConsistencyNotice("预约已成功但订单不可见，等待人工核对");
      }
      return view;
    }
    if (!Long.valueOf(reservation.getUserId()).equals(order.getUserId())
        || !Objects.equals(order.getCampaignId(), reservation.getCampaignId())
        || !Objects.equals(order.getCampaignGeneration(), reservation.getGeneration())) {
      view.setConsistencyNotice("订单与预约身份不一致，等待人工复核");
      view.setReservationNotice("预约状态待核对，请勿重复提交");
      return view;
    }
    view.setOrder(PurchaseOrderDTO.from(order));
    view.setReservationNotice(reservationNotice(reservation.getState(), order));
    if (!ReservationState.SUCCESS.name().equals(reservation.getState())) {
      view.setConsistencyNotice("订单已存在但预约状态未同步，等待人工核对");
    }
    return view;
  }

  private CampaignReservationDTO buildRedis(long orderId, Map<String, String> state) {
    CampaignReservationDTO view = new CampaignReservationDTO();
    view.setOrderId(orderId);
    view.setCampaignId(longNumber(state.get("campaignId")));
    view.setUserId(longNumber(state.get("userId")));
    view.setGeneration(number(state.get("generation")));
    String display = displayState(state.get("state"));
    view.setState(display);
    view.setRequestKey(state.get("requestKey"));
    view.setStockReleased("RELEASED".equals(display));
    view.setAcceptanceNotice("已受理预购请求，订单正在创建；受理不代表建单成功或已付款");
    view.setReservationNotice(stateNotice(display));
    view.setEntitlementNotice(ENTITLEMENT_NOTICE);
    return view;
  }

  private CampaignReservationDTO base(CampaignReservation reservation) {
    CampaignReservationDTO view = new CampaignReservationDTO();
    view.setOrderId(reservation.getOrderId());
    view.setCampaignId(reservation.getCampaignId());
    view.setUserId(reservation.getUserId());
    view.setGeneration(reservation.getGeneration());
    view.setState(reservation.getState());
    view.setStockReleased(Boolean.TRUE.equals(reservation.getStockReleased()));
    view.setRequestKey(reservation.getRequestKey());
    view.setAttempts(reservation.getAttempts());
    view.setLastError(reservation.getLastError());
    view.setCreatedAt(reservation.getCreatedAt());
    view.setReleasedAt(reservation.getReleasedAt());
    view.setAcceptanceNotice("已受理");
    view.setEntitlementNotice(ENTITLEMENT_NOTICE);
    return view;
  }

  private String reservationNotice(String state, PurchaseOrder order) {
    if (ReservationState.FAILED.name().equals(state)) {
      return "预约未成功，名额已释放，可重新参与";
    }
    if (ReservationState.REVIEW.name().equals(state)) {
      return "预约处理中，等待人工复核；请勿重复提交";
    }
    if (order == null) {
      return "已受理，订单正在创建；不代表建单成功或已付款";
    }
    if (OrderStatus.PAID.name().equals(order.getStatus())) {
      return "预约建单成功，订单已支付；不代表权益已到账";
    }
    if (OrderStatus.CANCELLED.name().equals(order.getStatus())) {
      return "预约订单已关闭，名额已释放；不表示发生过支付";
    }
    return "预约建单成功，订单待支付；支付完成前不会开通权益";
  }

  private String stateNotice(String state) {
    if ("FAILED".equals(state)) {
      return "预约未成功，名额已释放，可重新参与";
    }
    if ("RELEASED".equals(state)) {
      return "预约订单已关闭，名额已释放";
    }
    if ("BOOKED".equals(state)) {
      return "预约建单成功，订单状态同步中";
    }
    return "已受理，订单正在创建；不代表建单成功或已付款";
  }

  private String displayState(String state) {
    if (state == null || state.isEmpty()) {
      return ReservationState.PROCESSING.name();
    }
    return state;
  }

  private void decorate(CampaignOfferDTO offer) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    String state;
    String notice;
    if (CampaignState.PAUSED.name().equals(offer.getPublishState())) {
      state = "PAUSED";
      notice = "活动已暂停，暂不接受预约";
    } else if (now.isBefore(offer.getBeginAt())) {
      state = "UPCOMING";
      notice = "活动尚未开始，可提前查看，不代表已获得资格";
    } else if (now.isAfter(offer.getEndAt())) {
      state = "ENDED";
      notice = "活动已结束，不再接受预约";
    } else if (offer.getRemainingStock() == null || offer.getRemainingStock() <= 0) {
      state = "SOLD_OUT";
      notice = "可售名额已用尽；已受理的预约仍会继续处理";
    } else {
      state = "OPEN";
      notice = "可提交预约；受理不代表建单成功、已付款或权益到账";
    }
    offer.setState(state);
    offer.setSellable("OPEN".equals(state));
    offer.setDisplayNotice(notice);
  }

  private boolean sameUser(String value, long userId) {
    return value != null && String.valueOf(userId).equals(value);
  }

  private Integer number(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException failure) {
      return null;
    }
  }

  private Long longNumber(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException failure) {
      return null;
    }
  }
}
