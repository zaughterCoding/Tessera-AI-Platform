package com.aiplatform.campaign.mapper;

import com.aiplatform.campaign.entity.CampaignDeadLetter;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CampaignDeadLetterMapper {
  int insertIgnore(CampaignDeadLetter letter);

  CampaignDeadLetter selectById(@Param("messageId") String messageId);

  List<CampaignDeadLetter> selectByCampaign(@Param("campaignId") Long campaignId,
      @Param("limit") int limit);
}
