package com.aiplatform.cache;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 启动期不可用时只推迟跨实例失效订阅，不阻断应用启动；恢复后自动重新订阅。
 *
 * <p>Spring Data Redis 2.7 首次订阅失败会直接让容器启动抛出异常，导致整个应用启动失败。
 */
@Slf4j
public class ResilientRedisListenerContainer extends RedisMessageListenerContainer {
  private final MeterRegistry metrics;
  private final long retryMillis;
  private final AtomicBoolean deferred = new AtomicBoolean();
  private final AtomicBoolean retryScheduled = new AtomicBoolean();
  private final ScheduledExecutorService retries =
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cache-listener-retry");
        thread.setDaemon(true);
        return thread;
      });

  public ResilientRedisListenerContainer(MeterRegistry metrics, long retryMillis) {
    this.metrics = metrics;
    this.retryMillis = Math.max(200, retryMillis);
  }

  @Override
  public void start() {
    try {
      super.start();
      if (deferred.compareAndSet(true, false)) {
        metrics.counter("aiplatform.cache.invalidation", "result", "recovered").increment();
        log.info("缓存失效订阅已建立，跨实例本地失效恢复");
      }
    } catch (RuntimeException failure) {
      metrics.counter("aiplatform.cache.invalidation", "result", "deferred").increment();
      if (deferred.compareAndSet(false, true)) {
        log.warn("缓存失效订阅暂不可用，应用继续启动并在后台重试，type={}",
            failure.getClass().getSimpleName());
      }
      // 容器在订阅失败后仍保留已启动标记，先停止以重置内部状态，后续才能重新订阅。
      try {
        super.stop();
      } catch (RuntimeException ignored) {
        log.debug("停止未建立的缓存失效订阅时出现异常，type={}",
            ignored.getClass().getSimpleName());
      }
      scheduleRetry();
    }
  }

  private void scheduleRetry() {
    if (!retryScheduled.compareAndSet(false, true)) {
      return;
    }
    retries.schedule(() -> {
      retryScheduled.set(false);
      if (!isRunning()) {
        start();
      }
    }, retryMillis, TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  public void shutdownRetries() {
    retries.shutdownNow();
  }
}
