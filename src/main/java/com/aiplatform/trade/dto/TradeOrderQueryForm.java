package com.aiplatform.trade.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;

@Data
public class TradeOrderQueryForm {
  @Min(1)
  private Integer page = 1;

  @Min(1)
  @Max(20)
  private Integer size = 10;
}
