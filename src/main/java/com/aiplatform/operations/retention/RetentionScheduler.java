package com.aiplatform.operations.retention;

import com.aiplatform.operations.dto.RetentionRunDTO;
import com.aiplatform.operations.service.IRetentionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 按保留期截断已终结数据；每个目标独立执行，单个失败不影响其他目标。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.retention.cleanup-enabled", havingValue = "true",
    matchIfMissing = true)
public class RetentionScheduler {
  private final IRetentionService retention;

  public RetentionScheduler(IRetentionService retention) {
    this.retention = retention;
  }

  @Scheduled(fixedDelayString = "${app.retention.cleanup-interval-ms:86400000}",
      initialDelayString = "${app.retention.cleanup-interval-ms:86400000}")
  public void cleanup() {
    for (RetentionTarget target : RetentionTarget.values()) {
      try {
        RetentionRunDTO result = retention.run(target, 20);
        if (result.getDeleted() > 0) {
          log.info("保留清理完成，target={}，deleted={}，remaining={}",
              target.key(), result.getDeleted(), result.getRemaining());
        }
      } catch (RuntimeException failure) {
        log.warn("保留清理失败，target={}，type={}",
            target.key(), failure.getClass().getSimpleName());
      }
    }
  }
}
