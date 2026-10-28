package com.aiplatform.campaign.service;

import com.aiplatform.campaign.dto.AdminCampaignDTO;
import com.aiplatform.campaign.dto.CampaignAdminCreateForm;
import com.aiplatform.campaign.dto.CampaignDeadLetterDTO;
import com.aiplatform.campaign.dto.CampaignReconciliationDTO;
import com.aiplatform.campaign.dto.CampaignReservationDTO;
import com.aiplatform.campaign.dto.CampaignStreamMetricsDTO;
import java.util.List;

/** 管理端负责发布、暂停、恢复与人工复核；所有写操作都受 ADMIN 二次校验。 */
public interface ICampaignAdminService {
  AdminCampaignDTO create(CampaignAdminCreateForm form);

  AdminCampaignDTO publish(long id);

  AdminCampaignDTO pause(long id);

  AdminCampaignDTO recover(long id);

  List<AdminCampaignDTO> list();

  AdminCampaignDTO detail(long id);

  List<CampaignReservationDTO> reviewReservations(long campaignId, int limit);

  CampaignReservationDTO retry(long orderId);

  CampaignReservationDTO fail(long orderId, String reason);

  List<CampaignDeadLetterDTO> deadLetters(Long campaignId, int limit);

  void replayDeadLetter(String messageId);

  CampaignReconciliationDTO reconcile(long campaignId);

  CampaignStreamMetricsDTO streamMetrics();
}
