package com.aiplatform.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {
  public MessagingConfig(@Value("${app.messaging.transport:local}") String transport) {
    if (!"local".equals(transport) && !"rocketmq".equals(transport)) {
      throw new IllegalStateException("消息传输只能是 local 或 rocketmq");
    }
  }
}
