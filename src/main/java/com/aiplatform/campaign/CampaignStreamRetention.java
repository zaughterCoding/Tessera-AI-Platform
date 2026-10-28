package com.aiplatform.campaign;

import com.aiplatform.campaign.service.ICampaignReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 只在消费组、Pending、预约与交接都确认后才推进裁剪水位。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.campaign.trim-enabled", havingValue = "true",
    matchIfMissing = true)
public class CampaignStreamRetention {
  private final ICampaignReconciliationService reconciliation;

  public CampaignStreamRetention(ICampaignReconciliationService reconciliation) {
    this.reconciliation = reconciliation;
  }

  @Scheduled(fixedDelayString = "${app.campaign.trim-interval-ms:60000}")
  public void trim() {
    try {
      reconciliation.trimIfSafe();
    } catch (RuntimeException failure) {
      log.warn("活动 Stream 安全裁剪失败，type={}", failure.getClass().getSimpleName());
    }
  }
}
