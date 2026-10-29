package com.aiplatform.trade.service.impl;

import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import com.aiplatform.trade.service.IOrderLifecycleService;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderLifecycleServiceImpl implements IOrderLifecycleService {
  private final PurchaseOrderMapper orders;
  private final CampaignReservationMapper reservations;
  private final LimitedCampaignMapper campaigns;
  private final IBusinessOutboxService outbox;

  public OrderLifecycleServiceImpl(
      PurchaseOrderMapper orders,
      CampaignReservationMapper reservations,
      LimitedCampaignMapper campaigns,
      IBusinessOutboxService outbox) {
    this.orders = orders;
    this.reservations = reservations;
    this.campaigns = campaigns;
    this.outbox = outbox;
  }

  @Override
  @Transactional
  public boolean closeExpired(long orderId) {
    PurchaseOrder order = orders.selectOrderForUpdate(orderId);
    if (order == null || !OrderStatus.UNPAID.matches(order.getStatus())) {
      return false;
    }
    if (orders.closeExpired(orderId) == 1) {
      // 活动订单关闭与库存返还同一事务；释放标记保证返还只有一次。
      if (order.getCampaignId() != null) {
        releaseCampaignStock(order);
      }
      outbox.appendEvent(BusinessEventType.ORDER_CLOSED.name(), orderId, order.getUserId());
      return true;
    }
    // 行锁内仍为待支付，说明截止时间未到；重新登记截止事件，关闭责任不丢失。
    if (order.getExpireAt() != null) {
      outbox.appendEventAt(BusinessEventType.ORDER_EXPIRE.name(), orderId,
          order.getUserId(), order.getExpireAt());
    }
    return false;
  }

  @Override
  @Transactional
  public void cancelUnpaid(long userId, long orderId) {
    PurchaseOrder order = orders.selectOrderForUpdate(orderId);
    if (order == null || !Objects.equals(userId, order.getUserId())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (OrderStatus.CANCELLED.matches(order.getStatus())) {
      // 重复取消保持幂等；已关闭订单不会再释放第二次库存或登记第二条事件。
      return;
    }
    if (!OrderStatus.UNPAID.matches(order.getStatus())) {
      // 已支付订单不能伪装成退款；取消只适用于未支付状态。
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (orders.cancelUnpaid(orderId, userId) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (order.getCampaignId() != null) {
      releaseCampaignStock(order);
    }
    outbox.appendEvent(BusinessEventType.ORDER_CLOSED.name(), orderId, userId);
  }

  private void releaseCampaignStock(PurchaseOrder order) {
    CampaignReservation reservation = reservations.selectReservationForUpdate(order.getId());
    if (reservation == null
        || !Objects.equals(order.getCampaignId(), reservation.getCampaignId())
        || !ReservationState.SUCCESS.name().equals(reservation.getState())) {
      throw new IllegalStateException("活动订单与预约事实不一致");
    }
    if (reservations.markStockReleased(order.getId()) != 1) {
      return;
    }
    if (campaigns.returnStock(order.getCampaignId()) != 1) {
      throw new IllegalStateException("活动可售库存已超过初始库存");
    }
    outbox.appendEvent(BusinessEventType.CAMPAIGN_STOCK_RELEASED.name(),
        order.getId(), order.getUserId());
  }
}
