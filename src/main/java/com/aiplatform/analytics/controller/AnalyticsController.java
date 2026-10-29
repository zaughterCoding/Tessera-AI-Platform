package com.aiplatform.analytics.controller;

import com.aiplatform.analytics.dto.ApplicationActivityDTO;
import com.aiplatform.analytics.dto.PopularityItemDTO;
import com.aiplatform.analytics.service.IAnalyticsQueryService;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.security.PublicEndpoint;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 公开只读分析视图；只暴露聚合计数与公开应用字段。 */
@PublicEndpoint(rateLimited = true)
@Validated
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {
  private final IAnalyticsQueryService analytics;

  public AnalyticsController(IAnalyticsQueryService analytics) {
    this.analytics = analytics;
  }

  @GetMapping("/popularity")
  public Result<List<PopularityItemDTO>> popularity(
      @RequestParam(defaultValue = "5") int minutes,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
    return Result.ok(analytics.popularity(minutes, limit));
  }

  @GetMapping("/applications/{id}/overview")
  public Result<ApplicationActivityDTO> overview(
      @PathVariable @Positive Long id,
      @RequestParam(defaultValue = "5") int minutes) {
    return Result.ok(analytics.overview(id, minutes));
  }
}
