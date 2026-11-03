package com.aiplatform.generation;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.service.AttemptClaim;
import com.aiplatform.generation.service.GenerationDispatchService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
  private static final int MAX_SUBMIT_THREADS = 64;
  private static final long SHUTDOWN_WAIT_SECONDS = 10L;
  private final GenerationDispatchService dispatch;
  private final GenerationProperties properties;
  private final AtomicBoolean running = new AtomicBoolean();
  private final ExecutorService waker = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "generation-dispatch-wake");
    thread.setDaemon(true);
    return thread;
  });
  private final ExecutorService submitters;

  public GenerationDispatchScheduler(GenerationDispatchService dispatch,
      GenerationProperties properties) {
    this.dispatch = dispatch;
    this.properties = properties;
    // 认领数量已被渠道在途上限约束，线程数与上限一致时提交不会排队等待。
    this.submitters = Executors.newFixedThreadPool(
        Math.min(properties.getChannelMaxInFlight(), MAX_SUBMIT_THREADS), task -> {
          Thread thread = new Thread(task, "generation-submit");
          thread.setDaemon(true);
          return thread;
        });
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
        // 认领事务已经提交；提交不持锁也不占定时线程，慢上游不拖累其他定时任务。
        submitters.execute(() -> submit(claim));
      }
    } catch (RuntimeException failure) {
      log.warn("生成调度扫描失败，等待下次扫描 {}", failure.getClass().getSimpleName());
    } finally {
      running.set(false);
    }
  }

  private void submit(AttemptClaim claim) {
    try {
      dispatch.submitClaim(claim);
    } catch (RuntimeException failure) {
      // 写回失败时尝试仍带租约，到期后由恢复流程接管，不猜测上游结果。
      log.warn("生成提交写回失败，attemptId={}，type={}", claim.attemptId(),
          failure.getClass().getSimpleName());
    }
  }

  @PreDestroy
  public void shutdown() {
    waker.shutdownNow();
    submitters.shutdown();
    try {
      if (!submitters.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
        submitters.shutdownNow();
      }
    } catch (InterruptedException interrupted) {
      submitters.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
