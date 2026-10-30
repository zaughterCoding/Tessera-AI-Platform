package com.aiplatform.messaging.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.security.RequireAdmin;
import com.aiplatform.messaging.dto.MessageFailureDTO;
import com.aiplatform.messaging.dto.OutboxTaskDTO;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.entity.MessageFailure;
import com.aiplatform.messaging.service.IBusinessEventService;
import com.aiplatform.messaging.service.IOutboxRelayService;
import com.aiplatform.operations.audit.AuditOperation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 可靠任务与失败记录的人工复核入口。 */
@RequireAdmin
@Validated
@RestController
@RequestMapping("/admin/messaging")
public class AdminMessagingController {
  private final IOutboxRelayService relay;
  private final IBusinessEventService events;

  public AdminMessagingController(IOutboxRelayService relay, IBusinessEventService events) {
    this.relay = relay;
    this.events = events;
  }

  @GetMapping("/outbox")
  public Result<List<OutboxTaskDTO>> tasks(
      @RequestParam(required = false)
      @Pattern(regexp = "PENDING|SENDING|SENT|DONE|REVIEW") String state,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    List<OutboxTaskDTO> result = new ArrayList<>();
    for (BusinessOutboxTask task : relay.listTasks(state, limit, offset)) {
      result.add(OutboxTaskDTO.from(task));
    }
    return Result.ok(result);
  }

  @GetMapping("/outbox/{id}")
  public Result<OutboxTaskDTO> task(@PathVariable @Positive Long id) {
    BusinessOutboxTask task = relay.getTask(id);
    if (task == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return Result.ok(OutboxTaskDTO.from(task));
  }

  @PostMapping("/outbox/{id}/retry")
  @AuditOperation(action = "messaging.outbox.retry", objectType = "outbox")
  public Result<Void> retry(@PathVariable @Positive Long id) {
    if (!relay.retryReview(id)) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    return Result.ok();
  }

  @GetMapping("/failures")
  public Result<List<MessageFailureDTO>> failures(
      @RequestParam(required = false) @Pattern(regexp = "[a-z0-9-]{1,80}") String consumerGroup,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
      @RequestParam(defaultValue = "0") @Min(0) int offset) {
    List<MessageFailureDTO> result = new ArrayList<>();
    for (MessageFailure failure : events.listFailures(consumerGroup, limit, offset)) {
      result.add(MessageFailureDTO.from(failure));
    }
    return Result.ok(result);
  }
}
