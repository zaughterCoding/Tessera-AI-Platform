package com.aiplatform.generation;

import com.aiplatform.generation.service.GenerationResultService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 结果保存、结算恢复与孤儿清理各自独立扫描，单个失败不会阻塞其他恢复路径。 */
@Slf4j
@Component
public class GenerationResultScheduler {
  private final GenerationResultService results;
  private final AtomicBoolean saving = new AtomicBoolean();
  private final AtomicBoolean settling = new AtomicBoolean();
  private final AtomicBoolean sweeping = new AtomicBoolean();

  public GenerationResultScheduler(GenerationResultService results) {
    this.results = results;
  }

  @Scheduled(fixedDelayString = "${app.generation.result.scan-interval-ms:2000}")
  public void save() {
    if (!saving.compareAndSet(false, true)) {
      return;
    }
    try {
      results.processBatch();
    } catch (RuntimeException failure) {
      log.warn("结果保存扫描失败，等待下次扫描 {}", failure.getClass().getSimpleName());
    } finally {
      saving.set(false);
    }
  }

  @Scheduled(fixedDelayString = "${app.generation.result.settlement-interval-ms:5000}")
  public void settle() {
    if (!settling.compareAndSet(false, true)) {
      return;
    }
    try {
      results.settleBatch();
    } catch (RuntimeException failure) {
      log.warn("结算恢复扫描失败，等待下次扫描 {}", failure.getClass().getSimpleName());
    } finally {
      settling.set(false);
    }
  }

  @Scheduled(fixedDelayString = "${app.generation.result.orphan-scan-interval-ms:600000}")
  public void sweep() {
    if (!sweeping.compareAndSet(false, true)) {
      return;
    }
    try {
      results.sweepOrphans();
    } catch (RuntimeException failure) {
      log.warn("生成结果孤儿扫描失败，等待下次扫描 {}", failure.getClass().getSimpleName());
    } finally {
      sweeping.set(false);
    }
  }
}
