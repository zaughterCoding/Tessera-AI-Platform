package com.aiplatform.messaging.service.impl;

import com.aiplatform.cache.CacheClient;
import com.aiplatform.cache.CacheUnavailableException;
import com.aiplatform.catalog.mapper.UsageProductMapper;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.service.IBusinessProjectionService;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import com.aiplatform.trade.service.IOrderLifecycleService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 只处理当前阶段真实注册的事件；缓存内容可重建，数据库任务才是事实。 */
@Service
public class BusinessProjectionServiceImpl implements IBusinessProjectionService {
  private final CacheClient cache;
  private final UsageProductMapper products;
  private final PurchaseOrderMapper orders;
  private final IOrderLifecycleService lifecycle;
  private final String prefix;

  public BusinessProjectionServiceImpl(
      CacheClient cache,
      UsageProductMapper products,
      PurchaseOrderMapper orders,
      IOrderLifecycleService lifecycle,
      @Value("${app.redis.key-prefix}") String prefix) {
    this.cache = cache;
    this.products = products;
    this.orders = orders;
    this.lifecycle = lifecycle;
    this.prefix = prefix;
  }

  @Override
  public void project(BusinessOutboxTask task) {
    BusinessEventType type = BusinessEventType.from(task.getKind());
    switch (type) {
      case APPLICATION_INVALIDATE:
        invalidateApplication(task.getAggregateId());
        return;
      case PRODUCT_INVALIDATE:
        invalidateProduct(task);
        return;
      case ORDER_CREATED:
        projectOrder(task, null, null);
        return;
      case ORDER_PAID:
        projectOrder(task, OrderStatus.PAID.name(), FundingChannel.MOCK.name());
        return;
      case ORDER_CLAIMED:
        projectOrder(task, OrderStatus.PAID.name(), FundingChannel.FREE.name());
        return;
      case ORDER_CLOSED:
        projectOrder(task, OrderStatus.CANCELLED.name(), null);
        return;
      case ORDER_EXPIRE:
        closeExpiredOrder(task);
        return;
      default:
        throw new IllegalArgumentException("没有可用的投影处理器");
    }
  }

  private void invalidateApplication(long applicationId) {
    String base = prefix + "catalog:application:";
    boolean detail = cache.invalidate(base + applicationId);
    boolean products = cache.invalidate(prefix + "catalog:product:list:" + applicationId);
    int search = cache.invalidatePrefix(prefix + "catalog:application:search:");
    if (!detail || !products || search < 0) {
      throw new CacheUnavailableException("目录缓存失效未全部完成，等待任务重试");
    }
  }

  private void invalidateProduct(BusinessOutboxTask task) {
    long productId = task.getAggregateId();
    Long applicationId = products.selectApplicationId(productId);
    if (applicationId == null) {
      throw new IllegalArgumentException("商品不存在，无法完成缓存失效");
    }
    boolean detail = cache.invalidate(prefix + "catalog:product:" + productId);
    boolean list = cache.invalidate(prefix + "catalog:product:list:" + applicationId);
    if (!detail || !list) {
      throw new CacheUnavailableException("商品缓存失效未全部完成，等待任务重试");
    }
  }

  private void closeExpiredOrder(BusinessOutboxTask task) {
    PurchaseOrder order = requireOrder(task);
    // 事件只定位订单；是否到期和能否关闭由数据库行锁与条件更新决定。
    lifecycle.closeExpired(order.getId());
  }

  private void projectOrder(BusinessOutboxTask task, String expectedStatus,
      String expectedFunding) {
    PurchaseOrder order = requireOrder(task);
    if (expectedStatus != null && !expectedStatus.equals(order.getStatus())) {
      throw new IllegalArgumentException("订单状态与事件不一致");
    }
    if (expectedFunding != null && !expectedFunding.equals(order.getFunding())) {
      throw new IllegalArgumentException("订单资金来源与事件不一致");
    }
    // 投影按事件身份幂等，重复投递不会重复产生历史效果。
    orders.insertOrderEvent(task.getId(), order.getId(), task.getKind(), task.getCreatedAt());
  }

  private PurchaseOrder requireOrder(BusinessOutboxTask task) {
    PurchaseOrder order = orders.selectById(task.getAggregateId());
    if (order == null || !Objects.equals(order.getUserId(), task.getUserId())) {
      throw new IllegalArgumentException("订单与任务身份不一致");
    }
    return order;
  }
}
