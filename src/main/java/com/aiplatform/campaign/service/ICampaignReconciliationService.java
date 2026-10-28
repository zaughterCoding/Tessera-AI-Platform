package com.aiplatform.campaign.service;

import com.aiplatform.campaign.dto.CampaignReconciliationDTO;
import com.aiplatform.campaign.dto.CampaignStreamMetricsDTO;

/** 只读对账与安全裁剪；任何差异都不会自动改库存。 */
public interface ICampaignReconciliationService {
  CampaignReconciliationDTO reconcile(long campaignId);

  CampaignStreamMetricsDTO streamMetrics();

  boolean trimIfSafe();
}
