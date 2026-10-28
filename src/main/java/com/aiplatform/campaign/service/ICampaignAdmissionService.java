package com.aiplatform.campaign.service;

import com.aiplatform.campaign.dto.CampaignAdmissionDTO;
import com.aiplatform.campaign.dto.CampaignAdmissionForm;
import com.aiplatform.campaign.dto.CampaignOfferDTO;
import com.aiplatform.campaign.dto.CampaignReservationDTO;
import java.util.List;

/** 参与入口与本人预约查询；入口只判定资格，不直接承诺建单或付款。 */
public interface ICampaignAdmissionService {
  CampaignAdmissionDTO admit(long userId, CampaignAdmissionForm form);

  CampaignReservationDTO query(long userId, long orderId);

  CampaignReservationDTO queryByRequest(long userId, long campaignId, String requestKey);

  List<CampaignOfferDTO> offers();

  CampaignOfferDTO offer(long id);
}
