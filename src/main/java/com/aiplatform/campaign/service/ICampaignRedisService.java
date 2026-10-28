package com.aiplatform.campaign.service;

import com.aiplatform.campaign.CampaignMessage;
import com.aiplatform.campaign.CampaignStreamSnapshot;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import java.util.List;
import java.util.Map;

/** Redis 只承担入口原子判定和状态展示；数据库预约与订单才是最终事实。 */
public interface ICampaignRedisService {
  long admit(long orderId, long userId, long campaignId, String requestKey);

  String existingOrderForRequestKey(long campaignId, long userId, String requestKey);

  String existingOrderForUser(long campaignId, long userId);

  Map<String, String> requestState(long orderId);

  Long redisStock(long campaignId);

  Integer redisGeneration(long campaignId);

  boolean setActivityState(long campaignId, String state);

  String activityState(long campaignId);

  boolean markRequestState(long orderId, String state);

  long release(long campaignId, long userId, long orderId, int generation,
      String targetState, String requestKey, int initialStock);

  int rebuild(LimitedCampaign campaign, int generation, String state,
      List<CampaignReservation> activeReservations);

  /** 人工重试只补投原有身份，不重新预占库存。 */
  String enqueue(CampaignMessage message);

  CampaignStreamSnapshot snapshot();

  /** 只按最小 ID 裁剪；调用方必须先确认水位安全。 */
  long trimBelow(String minId);
}
