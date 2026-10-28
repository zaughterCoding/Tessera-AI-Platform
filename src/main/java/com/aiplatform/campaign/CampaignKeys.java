package com.aiplatform.campaign;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 活动相关的 Redis Key 统一带环境前缀，避免与其他环境或阶段共享状态。 */
@Component
public class CampaignKeys {
  private static final String ROOT = "campaign:";
  private final String prefix;
  private final String stream;
  private final String group;

  public CampaignKeys(@Value("${app.redis.key-prefix}") String prefix) {
    this.prefix = prefix;
    this.stream = prefix + ROOT + "stream:orders";
    this.group = prefix + ROOT + "fulfillment";
  }

  public String stock(long campaignId) {
    return prefix + ROOT + "stock:" + campaignId;
  }

  public String buyers(long campaignId) {
    return prefix + ROOT + "buyers:" + campaignId;
  }

  public String requests(long campaignId) {
    return prefix + ROOT + "requests:" + campaignId;
  }

  public String request(long orderId) {
    return prefix + ROOT + "request:" + orderId;
  }

  public String requestKey(long campaignId, long userId, String requestKey) {
    return prefix + ROOT + "request-key:" + campaignId + ":" + userId + ":" + requestKey;
  }

  public String activity(long campaignId) {
    return prefix + ROOT + "activity:" + campaignId;
  }

  /** 恢复时被带入新代次的订单；释放按新代次确认，旧事件不会误放新资格。 */
  public String carried(long campaignId) {
    return prefix + ROOT + "carried:" + campaignId;
  }

  public String stream() {
    return stream;
  }

  public String group() {
    return group;
  }

  public String prefix() {
    return prefix;
  }
}
