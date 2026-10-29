package com.aiplatform.operations.retention;

import javax.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 保留期只覆盖已终结、可确认不再重放的数据；在途与复核事实始终保留。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.retention")
public class RetentionProperties {
  private boolean cleanupEnabled = true;
  private long cleanupIntervalMs = 86400000L;
  private int outboxDoneDays = 30;
  private int inboxDays = 30;
  private int messageFailureDays = 30;
  private int analyticsFailureDays = 30;
  private int auditDays = 365;
  private int batchSize = 500;

  @PostConstruct
  public void validate() {
    requireDays("outbox-done-days", outboxDoneDays);
    requireDays("inbox-days", inboxDays);
    requireDays("message-failure-days", messageFailureDays);
    requireDays("analytics-failure-days", analyticsFailureDays);
    requireDays("audit-days", auditDays);
    if (batchSize < 1 || batchSize > 5000) {
      throw new IllegalArgumentException("保留清理批量必须在 1～5000 之间");
    }
    if (cleanupIntervalMs < 60000L) {
      throw new IllegalArgumentException("保留清理间隔不得小于 1 分钟");
    }
  }

  private void requireDays(String name, int days) {
    if (days < 1 || days > 3650) {
      throw new IllegalArgumentException("保留期 " + name + " 必须在 1～3650 天之间");
    }
  }
}
