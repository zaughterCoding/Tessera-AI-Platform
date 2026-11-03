package com.aiplatform.messaging;

import com.aiplatform.messaging.service.IOutboxRelayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 租约、重投与待发送任务的兜底轮询；失败不终止后续轮次。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.messaging.relay-enabled", havingValue = "true",
    matchIfMissing = true)
public class OutboxRelayScheduler {
  private final IOutboxRelayService relay;

  public OutboxRelayScheduler(IOutboxRelayService relay) {
    this.relay = relay;
  }

  @Scheduled(fixedDelayString = "${app.messaging.poll-delay-ms:1000}")
  public void poll() {
    try {
      relay.relayDueBatches();
      relay.redriveUnconsumedEvents();
    } catch (RuntimeException failure) {
      log.warn("可靠任务轮询失败，type={}", failure.getClass().getName());
    }
  }
}
