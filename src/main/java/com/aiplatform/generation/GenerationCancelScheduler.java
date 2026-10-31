package com.aiplatform.generation;

import com.aiplatform.generation.service.GenerationCancelService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 取消请求的持久重试由独立扫描驱动，渠道调用不在认领事务内执行。 */
@Slf4j
@Component
public class GenerationCancelScheduler {
  private final GenerationCancelService cancels;
  private final AtomicBoolean running = new AtomicBoolean();

  public GenerationCancelScheduler(GenerationCancelService cancels) {
    this.cancels = cancels;
  }

  @Scheduled(fixedDelayString = "${app.generation.result.cancel-scan-interval-ms:2000}")
  public void scan() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      cancels.processBatch();
    } catch (RuntimeException failure) {
      log.warn("取消扫描失败，等待下次扫描 {}", failure.getClass().getSimpleName());
    } finally {
      running.set(false);
    }
  }
}
