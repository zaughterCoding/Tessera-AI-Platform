package com.aiplatform.campaign.service;

import com.aiplatform.campaign.CampaignMessage;
import com.aiplatform.campaign.RegisterOutcome;

/** 预约登记与建单分离：登记先提交，建单失败可由 Pending 接管或人工重试。 */
public interface ICampaignFulfillmentService {
  RegisterOutcome register(CampaignMessage message, boolean handoff);

  void fulfill(long orderId);

  int recordFailure(long orderId, Throwable failure);

  void markReview(long orderId, String reason);
}
