package com.aiplatform.trade.service.impl;

import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import com.aiplatform.trade.service.IOrderLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderLifecycleServiceImpl implements IOrderLifecycleService {
  private final PurchaseOrderMapper orders;
  private final IBusinessOutboxService outbox;

  public OrderLifecycleServiceImpl(
      PurchaseOrderMapper orders, IBusinessOutboxService outbox) {
    this.orders = orders;
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
}
