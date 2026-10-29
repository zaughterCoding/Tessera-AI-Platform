package com.aiplatform.trade.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.aiplatform.trade.dto.OrderProductSnapshot;
import com.aiplatform.trade.dto.PurchaseOrderDTO;
import com.aiplatform.trade.dto.PurchaseOrderForm;
import com.aiplatform.trade.dto.TradeOrderQueryForm;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.entity.PurchasePayment;
import com.aiplatform.trade.entity.PurchaseRequest;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import com.aiplatform.trade.mapper.PurchasePaymentMapper;
import com.aiplatform.trade.service.IOrderLifecycleService;
import com.aiplatform.trade.service.ITradeOrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class TradeOrderServiceImpl implements ITradeOrderService {
  private static final int PRODUCT_ACTIVE = 1;
  private static final int APPLICATION_PUBLISHED = 1;
  private static final int STANDARD_SALE_MODE = 0;
  private static final String CURRENCY = "CNY";
  private final PurchaseOrderMapper orders;
  private final PurchasePaymentMapper payments;
  private final IBusinessOutboxService outbox;
  private final IOrderLifecycleService lifecycle;
  private final int timeoutMinutes;
  private final boolean mockEnabled;

  public TradeOrderServiceImpl(
      PurchaseOrderMapper orders,
      PurchasePaymentMapper payments,
      IBusinessOutboxService outbox,
      IOrderLifecycleService lifecycle,
      @Value("${app.trade.order-timeout-minutes:15}") int timeoutMinutes,
      @Value("${app.trade.mock-payment-enabled:false}") boolean mockEnabled) {
    this.orders = orders;
    this.payments = payments;
    this.outbox = outbox;
    this.lifecycle = lifecycle;
    this.timeoutMinutes = timeoutMinutes;
    this.mockEnabled = mockEnabled;
  }

  @Override
  @Transactional
  public PurchaseOrderDTO create(long userId, PurchaseOrderForm form) {
    String requestKey = form.getRequestKey().trim();
    long productId = form.getProductId();
    String digest = digest(productId);
    // 先读取可信商品再写幂等台账；无效商品不会留下任何请求行或订单。
    OrderProductSnapshot product = orders.selectOrderProduct(productId);
    if (!orderable(product)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    // 唯一键竞争而不是先查后插；重复键时锁定读取已提交的请求行。
    orders.insertRequestIfAbsent(userId, requestKey, digest);
    PurchaseRequest request = orders.selectRequestForUpdate(userId, requestKey);
    if (request == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    if (!digest.equals(request.getRequestDigest())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (request.getOrderId() != null) {
      PurchaseOrder existing = orders.selectOrderForUpdate(request.getOrderId());
      if (existing == null || !Long.valueOf(userId).equals(existing.getUserId())) {
        throw new ApiException(ErrorCode.UNAVAILABLE);
      }
      return PurchaseOrderDTO.from(existing);
    }
    boolean free = product.getPrice() != null && product.getPrice() == 0L;
    PurchaseOrder order = snapshot(userId, request.getId(), product);
    orders.insertOrder(order, free, timeoutMinutes);
    PurchaseOrder stored = orders.selectById(order.getId());
    if (stored == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    outbox.appendEvent(BusinessEventType.ORDER_CREATED.name(), stored.getId(), userId);
    if (free) {
      recordFreeClaim(stored);
      outbox.appendEvent(BusinessEventType.ORDER_CLAIMED.name(), stored.getId(), userId);
    } else {
      outbox.appendEventAt(BusinessEventType.ORDER_EXPIRE.name(), stored.getId(), userId,
          stored.getExpireAt());
    }
    if (orders.bindRequestOrder(request.getId(), stored.getId()) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    return PurchaseOrderDTO.from(stored);
  }

  @Override
  @Transactional
  public PurchaseOrderDTO pay(long userId, long orderId, long expectedAmount) {
    if (!mockEnabled) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    PurchaseOrder order = orders.selectOrderForUpdate(orderId);
    if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (expectedAmount != order.getAmount()) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (OrderStatus.PAID.matches(order.getStatus())) {
      return PurchaseOrderDTO.from(order);
    }
    if (!OrderStatus.UNPAID.matches(order.getStatus())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    // 条件更新同时校验归属、金额与数据库截止时间；与关闭路径互斥。
    if (orders.markPaid(orderId, userId, expectedAmount) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    PurchaseOrder paid = orders.selectOrderForUpdate(orderId);
    if (paid == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    PurchasePayment payment = new PurchasePayment();
    payment.setOrderId(orderId);
    payment.setUserId(userId);
    payment.setChannel(FundingChannel.MOCK.name());
    payment.setAmount(expectedAmount);
    payment.setSimulated(true);
    payment.setPaidAt(paid.getPaidAt());
    payments.insert(payment);
    outbox.appendEvent(BusinessEventType.ORDER_PAID.name(), orderId, userId);
    return PurchaseOrderDTO.from(paid);
  }

  @Override
  public PurchaseOrderDTO cancel(long userId, long orderId) {
    lifecycle.cancelUnpaid(userId, orderId);
    PurchaseOrder order = orders.selectById(orderId);
    if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return PurchaseOrderDTO.from(order);
  }

  @Override
  public PurchaseOrderDTO detail(long userId, long orderId) {
    PurchaseOrder order = orders.selectById(orderId);
    if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return PurchaseOrderDTO.from(order);
  }

  @Override
  public Result<List<PurchaseOrderDTO>> list(long userId, TradeOrderQueryForm form) {
    int pageNo = form.getPage() == null ? 1 : Math.max(1, form.getPage());
    int size = form.getSize() == null ? 10 : Math.max(1, Math.min(form.getSize(), 20));
    LambdaQueryWrapper<PurchaseOrder> query = new LambdaQueryWrapper<>();
    query.eq(PurchaseOrder::getUserId, userId).orderByDesc(PurchaseOrder::getId);
    IPage<PurchaseOrder> page = orders.selectPage(new Page<>(pageNo, size), query);
    List<PurchaseOrderDTO> views = new ArrayList<>();
    for (PurchaseOrder order : page.getRecords()) {
      views.add(PurchaseOrderDTO.from(order));
    }
    return Result.ok(views, page.getTotal());
  }

  @Override
  public int closeExpiredOrders(int batch) {
    int limit = Math.max(1, Math.min(batch, 500));
    int closed = 0;
    for (Long orderId : orders.selectExpiredOrderIds(limit)) {
      try {
        if (lifecycle.closeExpired(orderId)) {
          closed++;
        }
      } catch (RuntimeException failure) {
        // 单笔异常不阻塞同批其他订单；下一轮扫描会再次尝试同一到期订单。
        log.warn("订单到期关闭失败，orderId={}，type={}",
            orderId, failure.getClass().getSimpleName());
      }
    }
    return closed;
  }

  private boolean orderable(OrderProductSnapshot product) {
    return product != null
        && Integer.valueOf(PRODUCT_ACTIVE).equals(product.getProductStatus())
        && Integer.valueOf(APPLICATION_PUBLISHED).equals(product.getApplicationStatus())
        && Integer.valueOf(STANDARD_SALE_MODE).equals(product.getSaleMode());
  }

  private PurchaseOrder snapshot(
      long userId, long requestId, OrderProductSnapshot product) {
    PurchaseOrder order = new PurchaseOrder();
    order.setUserId(userId);
    order.setRequestId(requestId);
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

  private String digest(long productId) {
    return DigestUtil.sha256Hex("purchase-v1|product=" + productId);
  }
}
