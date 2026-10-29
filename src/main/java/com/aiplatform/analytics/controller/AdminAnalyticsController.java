package com.aiplatform.analytics.controller;

import com.aiplatform.analytics.dto.AnalyticsFailureDTO;
import com.aiplatform.analytics.dto.AnalyticsOutboxDTO;
import com.aiplatform.analytics.dto.BackfillStateDTO;
import com.aiplatform.analytics.entity.AnalyticsBackfillState;
import com.aiplatform.analytics.entity.AnalyticsFailureRecord;
import com.aiplatform.analytics.entity.AnalyticsOutboxEvent;
import com.aiplatform.analytics.mapper.AnalyticsFailureMapper;
import com.aiplatform.analytics.service.IAnalyticsBackfillService;
import com.aiplatform.analytics.service.IAnalyticsRelayService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.operations.audit.AuditOperation;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 分析任务、失败隔离与补数恢复的管理入口。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/analytics")
public class AdminAnalyticsController {
  private final IAnalyticsRelayService relay;
  private final IAnalyticsBackfillService backfill;
  private final AnalyticsFailureMapper failures;

  public AdminAnalyticsController(
      IAnalyticsRelayService relay,
      IAnalyticsBackfillService backfill,
      AnalyticsFailureMapper failures) {
    this.relay = relay;
    this.backfill = backfill;
    this.failures = failures;
  }

  @GetMapping("/backfill")
  public Result<BackfillStateDTO> backfillState() {
    AnalyticsBackfillState state = backfill.state();
    if (state == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return Result.ok(BackfillStateDTO.from(state));
  }

  @PostMapping("/backfill")
  @AuditOperation(action = "analytics.backfill.restart",
      objectType = "analytics-backfill", objectIdParam = "")
  public Result<Void> restartBackfill() {
    if (!backfill.restart()) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    return Result.ok();
  }

  @GetMapping("/failures")
  public Result<List<AnalyticsFailureDTO>> failures(
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    List<AnalyticsFailureDTO> result = new ArrayList<>();
    for (AnalyticsFailureRecord failure : failures.listFailures(limit, offset)) {
      result.add(AnalyticsFailureDTO.from(failure));
    }
    return Result.ok(result);
  }

  @GetMapping("/outbox")
  public Result<List<AnalyticsOutboxDTO>> outbox(
      @RequestParam(required = false)
      @Pattern(regexp = "PENDING|SENDING|SENT|REVIEW") String state,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    List<AnalyticsOutboxDTO> result = new ArrayList<>();
    for (AnalyticsOutboxEvent event : relay.listTasks(state, limit, offset)) {
      result.add(AnalyticsOutboxDTO.from(event));
    }
    return Result.ok(result);
  }

  @PostMapping("/outbox/{eventId}/retry")
  @AuditOperation(action = "analytics.outbox.retry",
      objectType = "analytics-outbox", objectIdParam = "eventId")
  public Result<Void> retry(
      @PathVariable @Pattern(regexp = "[A-Za-z0-9:_-]{1,64}") String eventId) {
    if (!relay.retryReview(eventId)) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    return Result.ok();
  }
}
