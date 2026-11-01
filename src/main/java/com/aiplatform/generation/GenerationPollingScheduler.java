package com.aiplatform.generation;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.service.AttemptClaim;
import com.aiplatform.generation.service.GenerationDispatchService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 轮询是回调丢失、进程重启与租约接管后的恢复路径；查询在认领事务提交后执行。 */
@Slf4j
@Component
public class GenerationPollingScheduler {
  private final GenerationDispatchService dispatch;
  private final GenerationProperties properties;
  private final AtomicBoolean running = new AtomicBoolean();

  public GenerationPollingScheduler(GenerationDispatchService dispatch,
      GenerationProperties properties) {
    this.dispatch = dispatch;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.generation.poll-scan-interval-ms:2000}")
  public void scan() {
    if (!properties.isPollingEnabled()) {
      return;
    }
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      List<AttemptClaim> claims = dispatch.claimDuePolls();
      for (AttemptClaim claim : claims) {
        dispatch.pollClaim(claim);
      }
    } catch (RuntimeException failure) {
      log.warn("生成轮询扫描失败，等待下次扫描 {}", failure.getClass().getSimpleName());
    } finally {
      running.set(false);
    }
  }
}
