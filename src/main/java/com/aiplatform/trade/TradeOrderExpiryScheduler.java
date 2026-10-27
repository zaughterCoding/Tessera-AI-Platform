package com.aiplatform.trade;

import com.aiplatform.trade.service.ITradeOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 截止事件的扫描兜底；与消息关闭共用同一行锁和条件更新路径。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.trade.expiry-scan-enabled", havingValue = "true",
    matchIfMissing = true)
public class TradeOrderExpiryScheduler {
  private final ITradeOrderService orders;
  private final int batch;

  public TradeOrderExpiryScheduler(
      ITradeOrderService orders,
      @Value("${app.trade.expiry-scan-batch:50}") int batch) {
    this.orders = orders;
    this.batch = Math.max(1, Math.min(batch, 500));
  }

  @Scheduled(fixedDelayString = "${app.trade.expiry-scan-ms:5000}")
  public void scan() {
    try {
      orders.closeExpiredOrders(batch);
    } catch (RuntimeException failure) {
      log.warn("到期订单扫描失败，type={}", failure.getClass().getName());
    }
  }
}
