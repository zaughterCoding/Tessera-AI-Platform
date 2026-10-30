package com.aiplatform.campaign.controller;

import com.aiplatform.campaign.dto.CampaignAdmissionDTO;
import com.aiplatform.campaign.dto.CampaignAdmissionForm;
import com.aiplatform.campaign.dto.CampaignOfferDTO;
import com.aiplatform.campaign.dto.CampaignReservationDTO;
import com.aiplatform.campaign.service.ICampaignAdmissionService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.PublicEndpoint;
import com.aiplatform.identity.security.UserHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 活动展示与本人预约查询；受理、建单成功和已付款始终分开表述。 */
@Validated
@RestController
@RequestMapping("/campaign")
public class CampaignController {
  private final ICampaignAdmissionService admission;

  public CampaignController(ICampaignAdmissionService admission) {
    this.admission = admission;
  }

  @PublicEndpoint
  @GetMapping("/offers")
  public Result<List<CampaignOfferDTO>> offers() {
    return Result.ok(admission.offers());
  }

  @PublicEndpoint
  @GetMapping("/offers/{id}")
  public Result<CampaignOfferDTO> offer(@PathVariable @Positive Long id) {
    return Result.ok(admission.offer(id));
  }

  @PostMapping("/reservations")
  public Result<CampaignAdmissionDTO> admit(
      @Valid @RequestBody CampaignAdmissionForm form) {
    return Result.ok(admission.admit(currentUserId(), form));
  }

  @GetMapping("/reservations/by-request")
  public Result<CampaignReservationDTO> byRequest(
      @RequestParam @Positive Long campaignId,
      @RequestParam @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestKey) {
    return Result.ok(admission.queryByRequest(currentUserId(), campaignId, requestKey));
  }

  @GetMapping("/reservations/{orderId}")
  public Result<CampaignReservationDTO> reservation(
      @PathVariable @Positive Long orderId) {
    return Result.ok(admission.query(currentUserId(), orderId));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
