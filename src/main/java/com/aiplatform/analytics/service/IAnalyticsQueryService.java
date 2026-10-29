package com.aiplatform.analytics.service;

import com.aiplatform.analytics.dto.ApplicationActivityDTO;
import com.aiplatform.analytics.dto.PopularityItemDTO;
import java.util.List;

/** 只读聚合查询；分析结果不参与交易、库存、权限或权益判断。 */
public interface IAnalyticsQueryService {
  ApplicationActivityDTO overview(long applicationId, int minutes);

  List<PopularityItemDTO> popularity(int minutes, int limit);
}
