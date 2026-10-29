package com.aiplatform.analytics.dto;

import com.aiplatform.analytics.entity.PopularityRow;
import lombok.Data;

/** 热榜条目只暴露公开应用字段与聚合计数，不含用户明细。 */
@Data
public class PopularityItemDTO {
  private int rank;
  private Long applicationId;
  private String name;
  private String summary;
  private String coverUrl;
  private Long visits;
  private Long uv;
  private Long orders;
  private Long payments;
  private Long claims;
  private Long heat;

  public static PopularityItemDTO from(int rank, PopularityRow row) {
    PopularityItemDTO dto = new PopularityItemDTO();
    dto.setRank(rank);
    dto.setApplicationId(row.getApplicationId());
    dto.setName(row.getName());
    dto.setSummary(row.getSummary());
    dto.setCoverUrl(row.getCoverUrl());
    dto.setVisits(row.getVisits());
    dto.setUv(row.getUv());
    dto.setOrders(row.getOrders());
    dto.setPayments(row.getPayments());
    dto.setClaims(row.getClaims());
    dto.setHeat(row.getHeat());
    return dto;
  }
}
