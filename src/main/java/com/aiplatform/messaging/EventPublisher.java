package com.aiplatform.messaging;

public interface EventPublisher {
  void publish(BusinessEvent event);
}
