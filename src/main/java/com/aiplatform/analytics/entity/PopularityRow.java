package com.aiplatform.analytics.entity;

import lombok.Data;

/** 热榜查询行；只包含公开应用字段与聚合计数。 */
@Data
public class PopularityRow {
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
}
