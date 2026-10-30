package com.aiplatform.analytics;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** 独立编解码器：接口层把 Long 序列化为字符串，内部消息保持数字类型。 */
@Component
public class AnalyticsEventCodec {
  private final JsonMapper json;

  public AnalyticsEventCodec() {
    this.json = JsonMapper.builder()
        // 内部消息允许新增字段，旧消费者保持兼容；未知动作仍按格式错误隔离。
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();
  }

  public String encode(AnalyticsEvent event) {
    try {
      return json.writeValueAsString(event);
    } catch (JacksonException failure) {
      throw new IllegalStateException("分析事件编码失败", failure);
    }
  }

  public AnalyticsEvent decode(String payload) {
    if (payload == null || payload.isEmpty()) {
      throw new AnalyticsMalformedException("分析事件载荷为空");
    }
    try {
      return json.readValue(payload, AnalyticsEvent.class);
    } catch (JacksonException failure) {
      throw new AnalyticsMalformedException("分析事件载荷无法解析");
    }
  }
}
