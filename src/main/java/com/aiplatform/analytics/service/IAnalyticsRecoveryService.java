package com.aiplatform.analytics.service;

/** 恢复已确认发送但消息存储中缺失的购买事实。 */
public interface IAnalyticsRecoveryService {
  /** 将超过观察窗口且没有消费明细的事实重新置为待发；返回本轮数量。 */
  int recoverUnconsumed();

  /** 管理入口：与定时恢复共用同一实现，额外执行 ADMIN 方法校验。 */
  int recoverAsAdmin();
}
