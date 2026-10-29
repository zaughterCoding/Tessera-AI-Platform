package com.aiplatform.trade.service;

public interface IOrderLifecycleService {
  /** 锁定订单并按数据库时间关闭；提前到达的截止事件会重新登记截止时间。 */
  boolean closeExpired(long orderId);

  /** 本人取消未支付订单；与付款、到期关闭共用行锁，活动订单同事务释放库存。 */
  void cancelUnpaid(long userId, long orderId);
}
