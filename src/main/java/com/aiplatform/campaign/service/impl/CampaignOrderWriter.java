package com.aiplatform.campaign.service.impl;

import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignOrderWriter;
import com.aiplatform.campaign.service.ICampaignRedisService;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.aiplatform.trade.dto.OrderProductSnapshot;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.entity.PurchasePayment;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import com.aiplatform.trade.mapper.PurchasePaymentMapper;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 订单、库存、预约状态和订单事件在同一事务提交；重复执行只保留已成立的事实。 */
@Slf4j
@Service
public class CampaignOrderWriter implements ICampaignOrderWriter {
  private static final int LIMITED_SALE_MODE = 1;
  private static final String CURRENCY = "CNY";
  private final PurchaseOrderMapper orders;
  private final PurchasePaymentMapper payments;
  private final CampaignReservationMapper reservations;
  private final LimitedCampaignMapper campaigns;
  private final IBusinessOutboxService outbox;
  private final ICampaignRedisService redis;
  private final int timeoutMinutes;

  public CampaignOrderWriter(
      PurchaseOrderMapper orders,
      PurchasePaymentMapper payments,
      CampaignReservationMapper reservations,
      LimitedCampaignMapper campaigns,
      IBusinessOutboxService outbox,
      ICampaignRedisService redis,
      @Value("${app.trade.order-timeout-minutes:15}") int timeoutMinutes) {
    this.orders = orders;
    this.payments = payments;
    this.reservations = reservations;
    this.campaigns = campaigns;
    this.outbox = outbox;
    this.redis = redis;
    this.timeoutMinutes = timeoutMinutes;
  }

  @Override
  @Transactional
  public void fulfill(long orderId) {
    CampaignReservation reservation = reservations.selectReservationForUpdate(orderId);
    if (reservation == null) {
      throw new IllegalArgumentException("预约不存在");
    }
    if (ReservationState.SUCCESS.name().equals(reservation.getState())) {
      return;
    }
    if (!ReservationState.PROCESSING.name().equals(reservation.getState())) {
      throw new IllegalStateException("预约状态不允许建单");
    }
    LimitedCampaign campaign = campaigns.selectById(reservation.getCampaignId());
    if (campaign == null || campaign.getPublishedAt() == null) {
      throw new IllegalArgumentException("活动不存在或尚未发布");
    }
    if (!Objects.equals(reservation.getGeneration(), campaign.getGeneration())) {
      throw new IllegalArgumentException("预约代次已过期");
    }
    OrderProductSnapshot product = orders.selectOrderProduct(campaign.getProductId());
    if (product == null
        || !Integer.valueOf(LIMITED_SALE_MODE).equals(product.getSaleMode())) {
      throw new IllegalArgumentException("活动商品不可建单");
    }
    PurchaseOrder existing = orders.selectById(orderId);
    if (existing != null) {
      verifyOrder(existing, reservation);
      reservations.markSuccess(orderId);
      return;
    }
    if (campaigns.consumeStock(campaign.getId()) != 1) {
      throw new IllegalStateException("活动库存不足，等待复核");
    }
    boolean free = product.getPrice() != null && product.getPrice() == 0L;
    PurchaseOrder order = snapshot(reservation, campaign, product);
    orders.insertCampaignOrder(order, free, timeoutMinutes);
    PurchaseOrder stored = orders.selectById(orderId);
    if (stored == null) {
      throw new IllegalStateException("活动订单写入后不可见");
    }
    verifyOrder(stored, reservation);
    outbox.appendEvent(BusinessEventType.ORDER_CREATED.name(), orderId, reservation.getUserId());
    if (free) {
      recordFreeClaim(stored);
      outbox.appendEvent(BusinessEventType.ORDER_CLAIMED.name(),
          orderId, reservation.getUserId());
    } else {
      outbox.appendEventAt(BusinessEventType.ORDER_EXPIRE.name(), orderId,
          reservation.getUserId(), stored.getExpireAt());
    }
    if (reservations.markSuccess(orderId) != 1) {
      throw new IllegalStateException("预约状态未能标记为建单成功");
    }
    markRedisAfterCommit(orderId);
  }

  private void verifyOrder(PurchaseOrder order, CampaignReservation reservation) {
    if (!Objects.equals(order.getUserId(), reservation.getUserId())
        || !Objects.equals(order.getCampaignId(), reservation.getCampaignId())
        || !Objects.equals(order.getCampaignGeneration(), reservation.getGeneration())) {
      throw new IllegalArgumentException("订单与预约身份不一致");
    }
  }

  private PurchaseOrder snapshot(CampaignReservation reservation, LimitedCampaign campaign,
      OrderProductSnapshot product) {
    PurchaseOrder order = new PurchaseOrder();
    order.setId(reservation.getOrderId());
    order.setUserId(reservation.getUserId());
    order.setCampaignId(campaign.getId());
    order.setCampaignGeneration(reservation.getGeneration());
    order.setProductId(product.getProductId());
    order.setApplicationId(product.getApplicationId());
    order.setApplicationName(product.getApplicationName());
    order.setApplicationVersion(product.getApplicationVersion());
    order.setProductTitle(product.getProductTitle());
    order.setProductSubtitle(product.getProductSubtitle());
    order.setProductRules(product.getProductRules());
    order.setModelVersion(product.getModelVersion());
    order.setImageSize(product.getImageSize());
    order.setOutputCount(product.getOutputCount());
    order.setCredits(product.getCredits());
    order.setValidDays(product.getValidDays());
    order.setSaleMode(product.getSaleMode());
    order.setCurrency(CURRENCY);
    order.setUnitPrice(product.getPrice());
    order.setAmount(product.getPrice());
    return order;
  }

  private void recordFreeClaim(PurchaseOrder order) {
    PurchasePayment payment = new PurchasePayment();
    payment.setOrderId(order.getId());
    payment.setUserId(order.getUserId());
    payment.setChannel(FundingChannel.FREE.name());
    payment.setAmount(0L);
    payment.setSimulated(false);
    payment.setPaidAt(order.getPaidAt());
    payments.insert(payment);
  }

  private void markRedisAfterCommit(long orderId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        try {
          redis.markRequestState(orderId, "BOOKED");
        } catch (RuntimeException failure) {
          // 展示状态可重建；数据库成功仍是唯一建单事实。
          log.warn("预约展示状态更新失败，orderId={}，type={}",
              orderId, failure.getClass().getSimpleName());
        }
      }
    });
  }
}
