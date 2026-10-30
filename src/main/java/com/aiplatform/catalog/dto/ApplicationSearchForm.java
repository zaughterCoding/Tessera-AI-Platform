package com.aiplatform.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 只接受检索条件；评分、销量、价格下限和归属不在请求白名单内。 */
@Data
public class ApplicationSearchForm {
  @Positive
  private Long categoryId;

  @Size(max = 50)
  private String keyword;

  @Pattern(regexp = "score|price|newest")
  private String sort = "score";

  @Min(1)
  @Max(10000)
  private int page = 1;

  @Min(1)
  @Max(20)
  private int size = 12;
}
