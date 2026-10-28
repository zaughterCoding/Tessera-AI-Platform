package com.aiplatform.campaign.service;

/** 单事务写入活动订单与库存事实；订单号来自受理阶段。 */
public interface ICampaignOrderWriter {
  void fulfill(long orderId);
}
