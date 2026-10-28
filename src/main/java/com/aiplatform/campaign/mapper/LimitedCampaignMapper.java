package com.aiplatform.campaign.mapper;

import com.aiplatform.campaign.dto.AdminCampaignDTO;
import com.aiplatform.campaign.dto.CampaignOfferDTO;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LimitedCampaignMapper extends BaseMapper<LimitedCampaign> {
  LimitedCampaign selectCampaignForUpdate(@Param("id") long id);

  List<CampaignOfferDTO> selectPublicOffers();

  CampaignOfferDTO selectPublicOffer(@Param("id") long id);

  List<AdminCampaignDTO> selectAdminOffers();

  AdminCampaignDTO selectAdminOffer(@Param("id") long id);

  int markPublished(@Param("id") long id);

  int updatePublishState(@Param("id") long id,
      @Param("from") String from,
      @Param("to") String to);

  int activateDraftGeneration(@Param("campaignId") long campaignId,
      @Param("generation") int generation);

  int insertGeneration(@Param("campaignId") long campaignId,
      @Param("generation") int generation,
      @Param("status") String status,
      @Param("remainingStock") int remainingStock);

  int replaceActiveGeneration(@Param("campaignId") long campaignId,
      @Param("replacement") int replacement);

  int updateAfterRecovery(@Param("id") long id,
      @Param("generation") int generation,
      @Param("publishState") String publishState);

  int consumeStock(@Param("id") long id);

  int returnStock(@Param("id") long id);

  long countReservations(@Param("campaignId") long campaignId);
}
