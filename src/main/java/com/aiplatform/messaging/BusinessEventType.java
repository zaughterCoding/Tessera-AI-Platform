package com.aiplatform.messaging;

/** 每种事件类型只有一个明确消费组，防止消息在错误处理器中被当成其他事实。 */
public enum BusinessEventType {
  APPLICATION_INVALIDATE("catalog-cache"),
  PRODUCT_INVALIDATE("catalog-cache"),
  ORDER_CREATED("order-events"),
  ORDER_PAID("order-events"),
  ORDER_CLAIMED("order-events"),
  ORDER_CLOSED("order-events"),
  ORDER_EXPIRE("order-deadlines"),
  CAMPAIGN_FULFILL("campaign-fulfillment"),
  CAMPAIGN_STOCK_RELEASED("campaign-events"),
  CAMPAIGN_RETRY("campaign-retry"),
  MEDIA_DELETE("media-lifecycle"),
  CREATION_PUBLISH("community-feed"),
  ENTITLEMENT_GRANT("entitlement-grants"),
  GENERATION_DISPATCH("generation-dispatch");

  private final String consumerGroup;

  BusinessEventType(String consumerGroup) {
    this.consumerGroup = consumerGroup;
  }

  public String consumerGroup() {
    return consumerGroup;
  }

  public static BusinessEventType from(String kind) {
    for (BusinessEventType type : values()) {
      if (type.name().equals(kind)) {
        return type;
      }
    }
    throw new IllegalArgumentException("不支持的事件类型");
  }
}
