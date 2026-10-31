package com.aiplatform.entitlement;

import com.aiplatform.entitlement.service.IEntitlementGrantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 到期扫描只是清理兜底；查询本身已按数据库时间隐藏过期可用次数。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.entitlement.expiry-scan-enabled", havingValue = "true",
    matchIfMissing = true)
public class EntitlementExpiryScheduler {
  private final IEntitlementGrantService grants;
  private final int batch;

  public EntitlementExpiryScheduler(
      IEntitlementGrantService grants,
      @Value("${app.entitlement.expiry-scan-batch:100}") int batch) {
    this.grants = grants;
    this.batch = Math.max(1, Math.min(batch, 500));
  }

  @Scheduled(fixedDelayString = "${app.entitlement.expiry-scan-ms:60000}")
  public void scan() {
    try {
      grants.expireDueGrants(batch);
    } catch (RuntimeException failure) {
      log.warn("权益到期扫描失败，type={}", failure.getClass().getName());
    }
  }
}
