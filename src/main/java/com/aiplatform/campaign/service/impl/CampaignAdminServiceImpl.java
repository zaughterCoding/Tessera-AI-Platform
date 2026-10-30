package com.aiplatform.campaign.service.impl;

import com.aiplatform.cache.ILock;
import com.aiplatform.cache.SimpleRedisLock;
import com.aiplatform.campaign.CampaignEntryGuard;
import com.aiplatform.campaign.CampaignSellability;
import com.aiplatform.campaign.CampaignStreamSnapshot;
import com.aiplatform.campaign.dto.AdminCampaignDTO;
import com.aiplatform.campaign.dto.CampaignAdminCreateForm;
import com.aiplatform.campaign.dto.CampaignDeadLetterDTO;
import com.aiplatform.campaign.dto.CampaignReconciliationDTO;
import com.aiplatform.campaign.dto.CampaignReservationDTO;
import com.aiplatform.campaign.dto.CampaignStreamMetricsDTO;
import com.aiplatform.campaign.entity.CampaignDeadLetter;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.enums.CampaignState;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignDeadLetterMapper;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignAdminService;
import com.aiplatform.campaign.service.ICampaignReconciliationService;
import com.aiplatform.campaign.service.ICampaignRedisService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.aiplatform.trade.dto.OrderProductSnapshot;
import com.aiplatform.trade.dto.PurchaseOrderDTO;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 活动库存只在建单时扣减；暂停先关入口，恢复先重建覆盖再替换旧代次。 */
@Slf4j
@Service
@PreAuthorize("hasRole('ADMIN')")
public class CampaignAdminServiceImpl implements ICampaignAdminService {
  private static final int REASON_LIMIT = 255;
  private static final int LOCK_SECONDS = 10;
  private static final int MAX_REVIEW_LIMIT = 200;
  private static final String ENTITLEMENT_NOTICE = "权益使用功能尚未开放";
  private static final String PREPARING = "PREPARING";
  private final LimitedCampaignMapper campaigns;
  private final CampaignReservationMapper reservations;
  private final CampaignDeadLetterMapper letters;
  private final PurchaseOrderMapper orders;
  private final ICampaignRedisService redis;
  private final IBusinessOutboxService outbox;
  private final BusinessOutboxMapper outboxMapper;
  private final ICampaignReconciliationService reconciliation;
  private final CampaignSellability sellability;
  private final CampaignEntryGuard entryGuard;
  private final StringRedisTemplate redisTemplate;
  private final TransactionTemplate transaction;
  private final String namespace;

  public CampaignAdminServiceImpl(
      LimitedCampaignMapper campaigns,
      CampaignReservationMapper reservations,
      CampaignDeadLetterMapper letters,
      PurchaseOrderMapper orders,
      ICampaignRedisService redis,
      IBusinessOutboxService outbox,
      BusinessOutboxMapper outboxMapper,
      ICampaignReconciliationService reconciliation,
      CampaignSellability sellability,
      CampaignEntryGuard entryGuard,
      StringRedisTemplate redisTemplate,
      PlatformTransactionManager transactionManager,
      @Value("${app.namespace}") String namespace) {
    this.campaigns = campaigns;
    this.reservations = reservations;
    this.letters = letters;
    this.orders = orders;
    this.redis = redis;
    this.outbox = outbox;
    this.outboxMapper = outboxMapper;
    this.reconciliation = reconciliation;
    this.sellability = sellability;
    this.entryGuard = entryGuard;
    this.redisTemplate = redisTemplate;
    this.transaction = new TransactionTemplate(transactionManager);
    this.namespace = namespace;
  }

  @Override
  @Transactional
  public AdminCampaignDTO create(CampaignAdminCreateForm form) {
    OrderProductSnapshot product = sellability.load(form.getProductId());
    if (!sellability.isSellable(product)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    if (!form.getBeginAt().isBefore(form.getEndAt()) || !form.getEndAt().isAfter(now)) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    LimitedCampaign campaign = new LimitedCampaign();
    campaign.setProductId(form.getProductId());
    campaign.setInitialStock(form.getInitialStock());
    campaign.setRemainingStock(form.getInitialStock());
    campaign.setBeginAt(form.getBeginAt());
    campaign.setEndAt(form.getEndAt());
    campaign.setPublishState(CampaignState.DRAFT.name());
    campaign.setGeneration(1);
    campaigns.insert(campaign);
    campaigns.insertGeneration(campaign.getId(), 1, "DRAFT", form.getInitialStock());
    return detail(campaign.getId());
  }

  @Override
  @Transactional
  public AdminCampaignDTO publish(long id) {
    LimitedCampaign campaign = campaigns.selectCampaignForUpdate(id);
    if (campaign == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (CampaignState.ACTIVE.name().equals(campaign.getPublishState())) {
      return detail(id);
    }
    if (!CampaignState.DRAFT.name().equals(campaign.getPublishState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    // 发布时再次核对商品与应用实时状态，避免把已失效商品开放出去。
    if (!sellability.isSellable(sellability.load(campaign.getProductId()))) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (campaigns.markPublished(id) != 1
        || campaigns.activateDraftGeneration(id, campaign.getGeneration()) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    int written = redis.rebuild(campaign, campaign.getGeneration(),
        CampaignState.ACTIVE.name(), Collections.emptyList());
    if (written < 0) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    entryGuard.unblock(id);
    return detail(id);
  }

  @Override
  @Transactional
  public AdminCampaignDTO pause(long id) {
    LimitedCampaign campaign = campaigns.selectCampaignForUpdate(id);
    if (campaign == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (CampaignState.PAUSED.name().equals(campaign.getPublishState())) {
      return detail(id);
    }
    if (!CampaignState.ACTIVE.name().equals(campaign.getPublishState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    // 先关闭入口，再改数据库状态；入口关闭失败时事务回滚，不会出现半开状态。
    redis.setActivityState(id, CampaignState.PAUSED.name());
    if (campaigns.updatePublishState(id, CampaignState.ACTIVE.name(),
        CampaignState.PAUSED.name()) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    return detail(id);
  }

  @Override
  public AdminCampaignDTO recover(long id) {
    ILock lock = new SimpleRedisLock("campaign:recover:" + id, redisTemplate, namespace);
    if (!lock.tryLock(LOCK_SECONDS)) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    try {
      return doRecover(id);
    } finally {
      lock.unlock();
    }
  }

  private AdminCampaignDTO doRecover(long id) {
    LimitedCampaign campaign = campaigns.selectById(id);
    if (campaign == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (CampaignState.DRAFT.name().equals(campaign.getPublishState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (CampaignState.ACTIVE.name().equals(campaign.getPublishState())) {
      String redisState = redis.activityState(id);
      Integer redisGeneration = readableGeneration(id);
      Long redisStock = readableStock(id);
      // 在途预约会让 Redis 库存低于数据库剩余；低库存可解释，高于剩余才是不可信。
      boolean stockUsable = redisStock != null && redisStock >= 0
          && redisStock <= campaign.getRemainingStock();
      boolean aligned = CampaignState.ACTIVE.name().equals(redisState)
          && Objects.equals(campaign.getGeneration(), redisGeneration)
          && stockUsable;
      if (aligned) {
        entryGuard.unblock(id);
        return detail(id);
      }
      if (PREPARING.equals(redisState)
          && Objects.equals(campaign.getGeneration(), redisGeneration)
          && stockUsable) {
        // 数据库代次已推进但最后一步未开启：核对后再放行，不重复推进代次。
        if (!redis.setActivityState(id, CampaignState.ACTIVE.name())) {
          throw new ApiException(ErrorCode.UNAVAILABLE);
        }
        entryGuard.unblock(id);
        return detail(id);
      }
      // 代次、库存或状态不一致时按数据库事实重建；未完成的重建同样重新执行。
      return rebuild(id, campaign);
    }
    if (!CampaignState.PAUSED.name().equals(campaign.getPublishState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    return rebuild(id, campaign);
  }

  /** 恢复先关闭入口并按数据库事实重建新代次，覆盖确认后才替换旧代次。 */
  private AdminCampaignDTO rebuild(long id, LimitedCampaign campaign) {
    entryGuard.block(id, "活动恢复重建中");
    if (reservations.countProcessingOrReview(id) > 0) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    CampaignStreamSnapshot stream = redis.snapshot();
    if (stream.isStreamExists() && !stream.isAllGroupsCaughtUp()) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (outboxMapper.countPendingCampaignEvents(id) > 0) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    List<CampaignReservation> active = reservations.selectActiveSuccess(id);
    long consistent = reservations.countUnreleasedSuccessWithOrder(id);
    if (active.size() != consistent) {
      throw new IllegalStateException("活动有效预约与订单事实不一致，不能重建");
    }
    redis.setActivityState(id, PREPARING);
    int nextGeneration = campaign.getGeneration() + 1;
    int written = redis.rebuild(campaign, nextGeneration, PREPARING, active);
    if (written != active.size()) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    // 重建覆盖确认后才在数据库替换旧代次；此前入口一直保持关闭。
    transaction.executeWithoutResult(status -> {
      campaigns.insertGeneration(id, nextGeneration, "ACTIVE", campaign.getRemainingStock());
      campaigns.replaceActiveGeneration(id, nextGeneration);
      if (campaigns.updateAfterRecovery(id, nextGeneration,
          CampaignState.ACTIVE.name()) != 1) {
        throw new IllegalStateException("活动代次更新失败");
      }
    });
    try {
      if (!redis.setActivityState(id, CampaignState.ACTIVE.name())) {
        throw new IllegalStateException("活动 Redis 状态缺失");
      }
    } catch (RuntimeException failure) {
      log.error("活动恢复后开启入口失败，campaignId={}，type={}",
          id, failure.getClass().getSimpleName());
      transaction.executeWithoutResult(status -> campaigns.updateAfterRecovery(
          id, nextGeneration, CampaignState.PAUSED.name()));
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    entryGuard.unblock(id);
    return detail(id);
  }

  private Integer readableGeneration(long id) {
    try {
      return redis.redisGeneration(id);
    } catch (RuntimeException failure) {
      return null;
    }
  }

  private Long readableStock(long id) {
    try {
      return redis.redisStock(id);
    } catch (RuntimeException failure) {
      return null;
    }
  }

  @Override
  public List<AdminCampaignDTO> list() {
    List<AdminCampaignDTO> views = campaigns.selectAdminOffers();
    for (AdminCampaignDTO view : views) {
      derive(view);
    }
    return views;
  }

  @Override
  public AdminCampaignDTO detail(long id) {
    AdminCampaignDTO view = campaigns.selectAdminOffer(id);
    if (view == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    derive(view);
    return view;
  }

  @Override
  public List<CampaignReservationDTO> reviewReservations(long campaignId, int limit) {
    List<CampaignReservation> rows = reservations.selectReview(
        campaignId, Math.max(1, Math.min(limit, MAX_REVIEW_LIMIT)));
    List<CampaignReservationDTO> views = new ArrayList<>();
    for (CampaignReservation row : rows) {
      views.add(adminView(row));
    }
    return views;
  }

  @Override
  @Transactional
  public CampaignReservationDTO retry(long orderId) {
    CampaignReservation reservation = reservations.selectReservationForUpdate(orderId);
    if (reservation == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (ReservationState.PROCESSING.name().equals(reservation.getState())) {
      return adminView(reservation);
    }
    if (!ReservationState.REVIEW.name().equals(reservation.getState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    LimitedCampaign campaign = campaigns.selectById(reservation.getCampaignId());
    if (campaign == null || campaign.getPublishedAt() == null
        || !Objects.equals(campaign.getGeneration(), reservation.getGeneration())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (reservations.resetForRetry(orderId) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    outbox.appendEvent(BusinessEventType.CAMPAIGN_RETRY.name(), orderId,
        reservation.getUserId());
    return adminView(reservations.selectReservation(orderId));
  }

  @Override
  @Transactional
  public CampaignReservationDTO fail(long orderId, String reason) {
    CampaignReservation reservation = reservations.selectReservationForUpdate(orderId);
    if (reservation == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (ReservationState.FAILED.name().equals(reservation.getState())) {
      return adminView(reservation);
    }
    if (ReservationState.SUCCESS.name().equals(reservation.getState())
        || orders.selectById(orderId) != null) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    String why = reason == null || reason.trim().isEmpty()
        ? "管理员判定预约未成立" : truncate(reason.trim());
    if (reservations.markFailed(orderId, why) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    outboxMapper.cancelCampaignFulfillment(orderId);
    outbox.appendEvent(BusinessEventType.CAMPAIGN_STOCK_RELEASED.name(), orderId,
        reservation.getUserId());
    LimitedCampaign campaign = campaigns.selectById(reservation.getCampaignId());
    if (campaign != null) {
      releaseAfterCommit(campaign, reservation);
    }
    return adminView(reservations.selectReservation(orderId));
  }

  @Override
  public List<CampaignDeadLetterDTO> deadLetters(Long campaignId, int limit) {
    List<CampaignDeadLetter> rows = letters.selectByCampaign(
        campaignId, Math.max(1, Math.min(limit, MAX_REVIEW_LIMIT)));
    List<CampaignDeadLetterDTO> views = new ArrayList<>();
    for (CampaignDeadLetter row : rows) {
      views.add(CampaignDeadLetterDTO.from(row));
    }
    return views;
  }

  @Override
  @Transactional
  public void replayDeadLetter(String messageId) {
    CampaignDeadLetter letter = letters.selectById(messageId);
    if (letter == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (letter.getOrderId() == null) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    CampaignReservation reservation =
        reservations.selectReservationForUpdate(letter.getOrderId());
    if (reservation == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (ReservationState.REVIEW.name().equals(reservation.getState())) {
      if (reservations.resetForRetry(reservation.getOrderId()) != 1) {
        throw new ApiException(ErrorCode.CONFLICT);
      }
    } else if (!ReservationState.PROCESSING.name().equals(reservation.getState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    outbox.appendEvent(BusinessEventType.CAMPAIGN_RETRY.name(),
        reservation.getOrderId(), reservation.getUserId());
  }

  @Override
  public CampaignReconciliationDTO reconcile(long campaignId) {
    return reconciliation.reconcile(campaignId);
  }

  @Override
  public CampaignStreamMetricsDTO streamMetrics() {
    return reconciliation.streamMetrics();
  }

  private CampaignReservationDTO adminView(CampaignReservation reservation) {
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
    PurchaseOrder order = orders.selectById(reservation.getOrderId());
    if (order != null
        && Objects.equals(order.getUserId(), reservation.getUserId())
        && Objects.equals(order.getCampaignId(), reservation.getCampaignId())
        && Objects.equals(order.getCampaignGeneration(), reservation.getGeneration())) {
      view.setOrder(PurchaseOrderDTO.from(order));
      view.setReservationNotice(ReservationState.SUCCESS.name().equals(reservation.getState())
          ? "预约建单成功" : "预约状态与订单待核对");
    } else if (ReservationState.FAILED.name().equals(reservation.getState())) {
      view.setReservationNotice("预约未成功，名额已释放，可重新参与");
    } else if (ReservationState.REVIEW.name().equals(reservation.getState())) {
      view.setReservationNotice("预约等待人工复核");
    } else {
      view.setReservationNotice("订单正在创建；不代表建单成功或已付款");
    }
    return view;
  }

  private void releaseAfterCommit(LimitedCampaign campaign, CampaignReservation reservation) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          long result = redis.release(campaign.getId(), reservation.getUserId(),
              reservation.getOrderId(), reservation.getGeneration(),
              ReservationState.FAILED.name(), reservation.getRequestKey(),
              campaign.getInitialStock());
          if (result < 0) {
            log.warn("预约失败补偿的同步释放未完成，orderId={}，result={}",
                reservation.getOrderId(), result);
          }
        } catch (RuntimeException failure) {
          // 释放事件仍在可靠任务中，下一轮会补偿。
          log.warn("预约失败补偿的同步释放异常，orderId={}，type={}",
              reservation.getOrderId(), failure.getClass().getSimpleName());
        }
      }
    });
  }

  private void derive(AdminCampaignDTO view) {
    if (CampaignState.DRAFT.name().equals(view.getPublishState())) {
      view.setState(CampaignState.DRAFT.name());
      return;
    }
    if (CampaignState.PAUSED.name().equals(view.getPublishState())) {
      view.setState(CampaignState.PAUSED.name());
      return;
    }
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    if (now.isBefore(view.getBeginAt())) {
      view.setState("UPCOMING");
    } else if (now.isAfter(view.getEndAt())) {
      view.setState("ENDED");
    } else if (view.getRemainingStock() == null || view.getRemainingStock() <= 0) {
      view.setState("SOLD_OUT");
    } else {
      view.setState("OPEN");
    }
  }

  private String truncate(String reason) {
    return reason.length() <= REASON_LIMIT ? reason : reason.substring(0, REASON_LIMIT);
  }
}
