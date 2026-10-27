package com.aiplatform.messaging.service;

import com.aiplatform.messaging.BusinessEvent;
import com.aiplatform.messaging.entity.MessageFailure;
import java.util.List;

public interface IBusinessEventService {
  void consume(BusinessEvent event, String consumerGroup);

  /**
   * 记录消费失败并按持久任务决定是否隔离。
   *
   * @return true 表示当前消息可以确认；false 表示仍需重投
   */
  boolean recordConsumptionFailure(
      BusinessEvent event,
      String consumerGroup,
      String brokerMessageId,
      String payload,
      Throwable failure,
      int deliveries);

  List<MessageFailure> listFailures(String consumerGroup, int limit, int offset);
}
