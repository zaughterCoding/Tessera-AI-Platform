package com.aiplatform.messaging;

import com.aiplatform.messaging.service.IOutboxRelayService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 将提交后的即时投递合并为一次后台唤醒，失败仍由轮询兜底。 */
@Slf4j
@Component
public class OutboxRelayTrigger {
  private final ObjectProvider<IOutboxRelayService> relay;
  private final boolean enabled;
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "outbox-relay-wakeup");
    thread.setDaemon(true);
    return thread;
  });

  public OutboxRelayTrigger(
      ObjectProvider<IOutboxRelayService> relay,
      @Value("${app.messaging.wakeup-enabled:true}") boolean enabled) {
    this.relay = relay;
    this.enabled = enabled;
  }

  public void wakeUp() {
    if (!enabled || !scheduled.compareAndSet(false, true)) {
      return;
    }
    executor.execute(() -> {
      scheduled.set(false);
      try {
        relay.getObject().relayOutboxBatch();
      } catch (RuntimeException failure) {
        log.warn("即时投递唤醒失败，等待轮询重试，type={}", failure.getClass().getName());
      }
    });
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }
}
