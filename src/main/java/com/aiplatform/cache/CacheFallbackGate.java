package com.aiplatform.cache;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Redis 不可用时的全局回源并发门，避免公开读把数据库压垮。 */
@Component
public class CacheFallbackGate {
  private final Semaphore permits;
  private final long acquireTimeoutMillis;

  public CacheFallbackGate(
      @Value("${app.cache.fallback.max-concurrent:4}") int maxConcurrent,
      @Value("${app.cache.fallback.acquire-timeout-ms:50}") long acquireTimeoutMillis) {
    this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
    this.acquireTimeoutMillis = Math.max(0, acquireTimeoutMillis);
  }

  public Permit acquire() {
    try {
      if (permits.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS)) {
        return new Permit(permits);
      }
      return null;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return null;
    }
  }

  public int availablePermits() {
    return permits.availablePermits();
  }

  /** 使用 try-with-resources 释放许可，避免异常路径把回源并发永久占满。 */
  public static final class Permit implements AutoCloseable {
    private final Semaphore permits;
    private boolean released;

    private Permit(Semaphore permits) {
      this.permits = permits;
    }

    @Override
    public void close() {
      if (!released) {
        released = true;
        permits.release();
      }
    }
  }
}
