package com.aiplatform.trade.service;

public interface IOrderLifecycleService {
  /** 锁定订单并按数据库时间关闭；提前到达的截止事件会重新登记截止时间。 */
  boolean closeExpired(long orderId);
}
