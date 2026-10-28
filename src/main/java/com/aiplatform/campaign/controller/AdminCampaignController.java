package com.aiplatform.campaign.controller;

import com.aiplatform.campaign.dto.AdminCampaignDTO;
import com.aiplatform.campaign.dto.CampaignAdminCreateForm;
import com.aiplatform.campaign.dto.CampaignDeadLetterDTO;
import com.aiplatform.campaign.dto.CampaignFailForm;
import com.aiplatform.campaign.dto.CampaignReconciliationDTO;
import com.aiplatform.campaign.dto.CampaignReservationDTO;
import com.aiplatform.campaign.dto.CampaignStreamMetricsDTO;
import com.aiplatform.campaign.service.ICampaignAdminService;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.security.RequireAdmin;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 活动运营入口；每次请求都由访问守卫重新读取角色并校验 ADMIN。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/campaigns")
public class AdminCampaignController {
  private final ICampaignAdminService campaigns;

  public AdminCampaignController(ICampaignAdminService campaigns) {
    this.campaigns = campaigns;
  }

  @PostMapping
  public Result<AdminCampaignDTO> create(
      @Valid @RequestBody CampaignAdminCreateForm form) {
    return Result.ok(campaigns.create(form));
  }

  @GetMapping
  public Result<List<AdminCampaignDTO>> list() {
    return Result.ok(campaigns.list());
  }

  @GetMapping("/{id}")
  public Result<AdminCampaignDTO> detail(@PathVariable @Positive Long id) {
    return Result.ok(campaigns.detail(id));
  }

  @PostMapping("/{id}/publish")
  public Result<AdminCampaignDTO> publish(@PathVariable @Positive Long id) {
    return Result.ok(campaigns.publish(id));
  }

  @PostMapping("/{id}/pause")
  public Result<AdminCampaignDTO> pause(@PathVariable @Positive Long id) {
    return Result.ok(campaigns.pause(id));
  }

  @PostMapping("/{id}/recover")
  public Result<AdminCampaignDTO> recover(@PathVariable @Positive Long id) {
    return Result.ok(campaigns.recover(id));
  }

  @GetMapping("/{id}/reconciliation")
  public Result<CampaignReconciliationDTO> reconciliation(
      @PathVariable @Positive Long id) {
    return Result.ok(campaigns.reconcile(id));
  }

  @GetMapping("/{id}/review")
  public Result<List<CampaignReservationDTO>> review(
      @PathVariable @Positive Long id,
      @RequestParam(defaultValue = "50") int limit) {
    return Result.ok(campaigns.reviewReservations(id, limit));
  }

  @PostMapping("/reservations/{orderId}/retry")
  public Result<CampaignReservationDTO> retry(@PathVariable @Positive Long orderId) {
    return Result.ok(campaigns.retry(orderId));
  }

  @PostMapping("/reservations/{orderId}/fail")
  public Result<CampaignReservationDTO> fail(
      @PathVariable @Positive Long orderId,
      @Valid @RequestBody(required = false) CampaignFailForm form) {
    return Result.ok(campaigns.fail(orderId, form == null ? null : form.getReason()));
  }

  @GetMapping("/dead-letters")
  public Result<List<CampaignDeadLetterDTO>> deadLetters(
      @RequestParam(required = false) Long campaignId,
      @RequestParam(defaultValue = "50") int limit) {
    return Result.ok(campaigns.deadLetters(campaignId, limit));
  }

  @PostMapping("/dead-letters/{messageId}/replay")
  public Result<Void> replay(
      @PathVariable @Size(min = 1, max = 64) String messageId) {
    campaigns.replayDeadLetter(messageId);
    return Result.ok();
  }

  @GetMapping("/stream-metrics")
  public Result<CampaignStreamMetricsDTO> streamMetrics() {
    return Result.ok(campaigns.streamMetrics());
  }
}
