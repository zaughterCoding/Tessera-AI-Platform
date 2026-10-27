package com.aiplatform.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSummaryDTO {
  private Long id;
  private Long categoryId;
  private String categoryName;
  private String name;
  private String summary;
  private String capabilities;
  private String coverUrl;
  private Long priceFrom;
  private Integer score;
  private Integer reviewCount;
  private Integer soldCount;
  private Integer applicationVersion;
}
