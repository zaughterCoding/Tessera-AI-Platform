package com.aiplatform.messaging;

import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.service.IBusinessEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 单进程传输：与 Broker 一样按任务身份投递，失败仍落到持久任务。 */
@Component
@ConditionalOnProperty(name = "app.messaging.transport", havingValue = "local",
    matchIfMissing = true)
public class LocalEventPublisher implements EventPublisher {
  private final IBusinessEventService consumer;
  private final ObjectMapper json;

  public LocalEventPublisher(IBusinessEventService consumer, ObjectMapper json) {
    this.consumer = consumer;
    this.json = json;
  }

  @Override
  public void publish(BusinessEvent event) {
    String group = BusinessEventType.from(event.getKind()).consumerGroup();
    String payload = payload(event);
    try {
      consumer.consume(event, group);
    } catch (RuntimeException failure) {
      // 失败已持久化时仍视为投递完成；未持久化则抛出，由发送侧记录并重试。
      boolean recorded = consumer.recordConsumptionFailure(
          event, group, "local:" + event.getEventId(), payload, failure, 0);
      if (!recorded) {
        throw failure;
      }
    }
  }

  private String payload(BusinessEvent event) {
    try {
      return json.writeValueAsString(event);
    } catch (JacksonException failure) {
      return "";
    }
  }
}
