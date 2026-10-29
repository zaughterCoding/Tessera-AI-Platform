package com.aiplatform.campaign;

import com.aiplatform.campaign.dto.CampaignReconciliationDTO;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignReconciliationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 周期性只读对账：只记录差异并暴露指标，绝不自动改库存。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.campaign.reconcile-enabled", havingValue = "true",
    matchIfMissing = true)
public class CampaignReconciliationScheduler {
  private final LimitedCampaignMapper campaigns;
  private final ICampaignReconciliationService reconciliation;
  private final MeterRegistry metrics;
  private final AtomicLong mismatched = new AtomicLong();

  public CampaignReconciliationScheduler(
      LimitedCampaignMapper campaigns,
      ICampaignReconciliationService reconciliation,
      MeterRegistry metrics) {
    this.campaigns = campaigns;
    this.reconciliation = reconciliation;
    this.metrics = metrics;
    metrics.gauge("aiplatform.campaign.stock.mismatch", mismatched);
  }

  @Scheduled(fixedDelayString = "${app.campaign.reconcile-interval-ms:60000}")
  public void reconcile() {
    List<LimitedCampaign> published = campaigns.selectList(
        new LambdaQueryWrapper<LimitedCampaign>()
            .isNotNull(LimitedCampaign::getPublishedAt));
    AtomicLong currentMismatch = new AtomicLong();
    for (LimitedCampaign campaign : published) {
      try {
        CampaignReconciliationDTO view = reconciliation.reconcile(campaign.getId());
        metrics.counter("aiplatform.campaign.reconciliation",
            "campaign", String.valueOf(campaign.getId()),
            "formula", view.isFormulaMatched() ? "matched" : "mismatched",
            "exact", view.isExact() ? "exact" : "inexact").increment();
        if (!view.isFormulaMatched() || !view.isExact()) {
          currentMismatch.incrementAndGet();
          log.warn("活动对账存在差异，campaignId={}，formula={}，exact={}，note={}",
              campaign.getId(), view.isFormulaMatched(), view.isExact(), view.getNote());
        }
      } catch (RuntimeException failure) {
        log.warn("活动对账失败，campaignId={}，type={}",
            campaign.getId(), failure.getClass().getSimpleName());
      }
    }
    mismatched.set(currentMismatch.get());
  }
}
