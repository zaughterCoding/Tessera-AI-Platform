package com.aiplatform.campaign;

import java.util.Map;
import lombok.Data;

/** Stream 消息只用于定位已受理身份；可信事实仍从数据库读取。 */
@Data
public class CampaignMessage {
  private long orderId;
  private long userId;
  private long campaignId;
  private int generation;
  private String requestKey;

  public static CampaignMessage parse(Map<String, String> fields) {
    CampaignMessage message = new CampaignMessage();
    message.orderId = number(fields, "orderId");
    message.userId = number(fields, "userId");
    message.campaignId = number(fields, "campaignId");
    message.generation = (int) number(fields, "generation");
    message.requestKey = fields.get("requestKey");
    if (message.orderId <= 0 || message.userId <= 0
        || message.campaignId <= 0 || message.generation <= 0) {
      throw new IllegalArgumentException("预约消息身份不可信");
    }
    return message;
  }

  private static long number(Map<String, String> fields, String name) {
    String value = fields.get(name);
    if (value == null) {
      throw new IllegalArgumentException("预约消息缺少字段");
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException("预约消息字段不是数字", failure);
    }
  }
}
