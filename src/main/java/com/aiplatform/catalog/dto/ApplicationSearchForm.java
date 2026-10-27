package com.aiplatform.catalog.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
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
