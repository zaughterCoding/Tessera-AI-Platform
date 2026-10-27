package com.aiplatform.catalog.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailDTO {
  private Long id;
  private Long categoryId;
  private String categoryName;
  private String name;
  private String summary;
  private String description;
  private String capabilities;
  private String coverUrl;
  private Long priceFrom;
  private Integer score;
  private Integer reviewCount;
  private Integer soldCount;
  private Integer applicationVersion;
  private String modelName;
  private String modelVersion;
  private LocalDateTime publishedAt;
}
