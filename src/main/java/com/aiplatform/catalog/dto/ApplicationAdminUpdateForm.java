package com.aiplatform.catalog.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import lombok.Data;

/** 管理写白名单：统计、归属、版本和发布时间不可由请求改写。 */
@Data
public class ApplicationAdminUpdateForm {
  @Positive
  private Long categoryId;

  @Size(max = 128)
  private String name;

  @Size(max = 255)
  private String summary;

  @Size(max = 10000)
  private String description;

  @Size(max = 512)
  private String capabilities;

  @Size(max = 512)
  private String coverUrl;

  @Size(max = 64)
  private String modelName;

  @Size(max = 64)
  private String modelVersion;

  @Min(0)
  private Long priceFrom;

  @Min(1)
  @Max(2)
  private Integer status;
}
