package com.aiplatform.generation;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.service.AttemptClaim;
import com.aiplatform.generation.service.GenerationDispatchService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 定时扫描与消息唤醒都只触发有界认领；真正执行仍由数据库交接行与容量条件决定。 */
@Slf4j
@Component
public class GenerationDispatchScheduler {
  private final GenerationDispatchService dispatch;
  private final GenerationProperties properties;
  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService waker = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "generation-dispatch-wake");
    thread.setDaemon(true);
    return thread;
  });

  public GenerationDispatchScheduler(GenerationDispatchService dispatch,
      GenerationProperties properties) {
    this.dispatch = dispatch;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.generation.dispatch-interval-ms:2000}")
  public void scan() {
    runOnce();
  }

  /** 事务提交后再唤醒，避免消费线程看到尚未提交的交接行。 */
  public void wake() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          waker.execute(GenerationDispatchScheduler.this::runOnce);
        }
      });
      return;
    }
    waker.execute(this::runOnce);
  }

  private void runOnce() {
    if (!properties.isSchedulingEnabled()) {
      return;
    }
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      List<AttemptClaim> claims = dispatch.claimDueHandoffs();
      for (AttemptClaim claim : claims) {
        // 认领事务已经提交；网络提交不持有任何业务行锁。
        dispatch.submitClaim(claim);
      }
    } catch (RuntimeException failure) {
      log.warn("生成调度扫描失败，等待下次扫描 {}", failure.getClass().getSimpleName());
    } finally {
      running.set(false);
    }
  }

  @PreDestroy
  public void shutdown() {
    waker.shutdownNow();
  }
}
