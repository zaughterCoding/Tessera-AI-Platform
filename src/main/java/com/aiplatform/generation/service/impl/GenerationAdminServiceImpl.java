package com.aiplatform.generation.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.entitlement.mapper.GenerationSettlementMapper;
import com.aiplatform.generation.GenerationCancelState;
import com.aiplatform.generation.GenerationResultState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.config.SimulatorFaultControlPolicy;
import com.aiplatform.generation.dto.GenerationAdminActionResultDTO;
import com.aiplatform.generation.dto.GenerationAdminDeliveryDTO;
import com.aiplatform.generation.dto.GenerationAdminOverviewDTO;
import com.aiplatform.generation.dto.GenerationAdminResultDTO;
import com.aiplatform.generation.dto.GenerationAdminSettingsDTO;
import com.aiplatform.generation.dto.GenerationAdminTaskDTO;
import com.aiplatform.generation.dto.GenerationCancelDTO;
import com.aiplatform.generation.dto.GenerationChannelDTO;
import com.aiplatform.generation.dto.GenerationReconcileResultDTO;
import com.aiplatform.generation.dto.GenerationSettlementDTO;
import com.aiplatform.generation.dto.GenerationUserSchedulingDTO;
import com.aiplatform.generation.dto.GenerationUserSchedulingForm;
import com.aiplatform.generation.dto.SimulatorFaultForm;
import com.aiplatform.generation.entity.GenerationCancelRequest;
import com.aiplatform.generation.entity.GenerationResult;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.GenerationCancelMapper;
import com.aiplatform.generation.mapper.GenerationHandoffMapper;
import com.aiplatform.generation.mapper.GenerationHandoffMapper.HandoffRow;
import com.aiplatform.generation.mapper.GenerationQuotaMapper;
import com.aiplatform.generation.mapper.GenerationQuotaMapper.SchedulingRow;
import com.aiplatform.generation.mapper.GenerationResultMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.mapper.ProviderCallbackMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import com.aiplatform.generation.mapper.SimulatedProviderMapper;
import com.aiplatform.generation.service.GenerationCancelTransactions;
import com.aiplatform.generation.service.GenerationResultTransactions;
import com.aiplatform.generation.service.GenerationSettlementTransactions;
import com.aiplatform.generation.service.IGenerationAdminService;
import com.aiplatform.identity.mapper.UserMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理查询只读取事实；恢复入口只提前检查时间，不直接改写状态或余额。 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class GenerationAdminServiceImpl implements IGenerationAdminService {
  private static final int MAX_LIMIT = 100;
  private static final Set<String> TASK_STATES = Set.of(
      GenerationTaskState.QUEUED, GenerationTaskState.DISPATCHING,
      GenerationTaskState.SUBMITTED, GenerationTaskState.RUNNING,
      GenerationTaskState.RECONCILING, GenerationTaskState.RESULT_PENDING,
      GenerationTaskState.FAILED_PENDING_FOLLOWUP,
      GenerationTaskState.CANCELLED_PENDING_FOLLOWUP,
      GenerationTaskState.QUEUE_TIMEOUT, GenerationTaskState.REVIEW,
      GenerationTaskState.CANCEL_REQUESTED, GenerationTaskState.SUCCEEDED,
      GenerationTaskState.FAILED, GenerationTaskState.CANCELLED);
  private static final List<String> TASK_ORDER = List.of(
      GenerationTaskState.QUEUED, GenerationTaskState.DISPATCHING,
      GenerationTaskState.SUBMITTED, GenerationTaskState.RUNNING,
      GenerationTaskState.RECONCILING, GenerationTaskState.RESULT_PENDING,
      GenerationTaskState.FAILED_PENDING_FOLLOWUP,
      GenerationTaskState.CANCELLED_PENDING_FOLLOWUP,
      GenerationTaskState.QUEUE_TIMEOUT, GenerationTaskState.REVIEW,
      GenerationTaskState.CANCEL_REQUESTED, GenerationTaskState.SUCCEEDED,
      GenerationTaskState.FAILED, GenerationTaskState.CANCELLED);
  private static final List<String> RESULT_ORDER = List.of(
      GenerationResultState.PENDING, GenerationResultState.SAVING,
      GenerationResultState.SAVED, GenerationResultState.DELIVERED,
      GenerationResultState.FAILED);
  private static final List<String> CANCEL_ORDER = List.of(
      GenerationCancelState.PENDING, GenerationCancelState.RETRYING,
      GenerationCancelState.RESOLVED, GenerationCancelState.REJECTED,
      GenerationCancelState.UNSUPPORTED);
  private static final List<String> ATTEMPT_ORDER = List.of(
      ProviderAttemptState.SUBMIT_PENDING, ProviderAttemptState.SUBMITTING,
      ProviderAttemptState.ACCEPTED, ProviderAttemptState.RUNNING,
      ProviderAttemptState.RECONCILING, ProviderAttemptState.RESULT_PENDING,
      ProviderAttemptState.FAILED, ProviderAttemptState.CANCELLED,
      ProviderAttemptState.REVIEW);
  private static final List<String> HANDOFF_ORDER =
      List.of("PENDING", "CLAIMED", "RETRY", "COMPLETED");
  private static final Set<String> FAULT_MODES = Set.of(
      "REJECT_SUBMIT", "LOSE_SUBMIT_RESPONSE", "TIMEOUT_BUT_RUNNING", "SLOW", "FAIL",
      "QUERY_TEMPORARY_FAILURE");

  private final GenerationTaskMapper tasks;
  private final ProviderAttemptMapper attempts;
  private final ProviderChannelMapper channels;
  private final GenerationHandoffMapper handoffs;
  private final ProviderCallbackMapper callbacks;
  private final SimulatedProviderMapper simulator;
  private final GenerationResultMapper results;
  private final GenerationCancelMapper cancels;
  private final GenerationSettlementMapper settlements;
  private final GenerationResultTransactions resultTransactions;
  private final GenerationSettlementTransactions settlementTransactions;
  private final GenerationCancelTransactions cancelTransactions;
  private final GenerationProperties properties;
  private final SimulatorFaultControlPolicy faultControlPolicy;
  private final GenerationQuotaMapper quotas;
  private final UserMapper users;

  public GenerationAdminServiceImpl(GenerationTaskMapper tasks, ProviderAttemptMapper attempts,
      ProviderChannelMapper channels, GenerationHandoffMapper handoffs,
      ProviderCallbackMapper callbacks, SimulatedProviderMapper simulator,
      GenerationResultMapper results, GenerationCancelMapper cancels,
      GenerationSettlementMapper settlements, GenerationResultTransactions resultTransactions,
      GenerationSettlementTransactions settlementTransactions,
      GenerationCancelTransactions cancelTransactions, GenerationProperties properties,
      SimulatorFaultControlPolicy faultControlPolicy, GenerationQuotaMapper quotas,
      UserMapper users) {
    this.quotas = quotas;
    this.users = users;
    this.tasks = tasks;
    this.attempts = attempts;
    this.channels = channels;
    this.handoffs = handoffs;
    this.callbacks = callbacks;
    this.simulator = simulator;
    this.results = results;
    this.cancels = cancels;
    this.settlements = settlements;
    this.resultTransactions = resultTransactions;
    this.settlementTransactions = settlementTransactions;
    this.cancelTransactions = cancelTransactions;
    this.properties = properties;
    this.faultControlPolicy = faultControlPolicy;
  }

  @Override
  public GenerationAdminOverviewDTO overview() {
    Map<String, Integer> taskCounts = new LinkedHashMap<>();
    for (String state : TASK_ORDER) {
      taskCounts.put(state, tasks.countByState(state));
    }
    Map<String, Integer> attemptCounts = new LinkedHashMap<>();
    for (String state : ATTEMPT_ORDER) {
      attemptCounts.put(state, attempts.countByState(state));
    }
    Map<String, Integer> handoffCounts = new LinkedHashMap<>();
    for (String state : HANDOFF_ORDER) {
      handoffCounts.put(state, handoffs.countByState(state));
    }
    Map<String, Integer> resultCounts = new LinkedHashMap<>();
    for (String state : RESULT_ORDER) {
      resultCounts.put(state, (int) results.countByState(state));
    }
    Map<String, Integer> cancelCounts = new LinkedHashMap<>();
    for (String state : CANCEL_ORDER) {
      cancelCounts.put(state, (int) cancels.countByState(state));
    }
    List<GenerationChannelDTO> channelViews = new ArrayList<>();
    for (ProviderChannel channel : channels.selectList(null)) {
      channelViews.add(GenerationChannelDTO.from(channel));
    }
    return new GenerationAdminOverviewDTO(taskCounts, attemptCounts, handoffCounts,
        resultCounts, cancelCounts, channelViews, callbacks.countConflicts(),
        simulator.countJobs(), settings());
  }

  @Override
  public List<GenerationAdminTaskDTO> tasks(String state, int limit, int offset) {
    if (state != null && !TASK_STATES.contains(state)) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    int boundedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    int boundedOffset = Math.max(0, offset);
    List<GenerationTask> rows = tasks.selectRecentTasks(state, boundedLimit, boundedOffset);
    if (rows.isEmpty()) {
      return List.of();
    }
    List<Long> taskIds = rows.stream().map(GenerationTask::getId).toList();
    Map<Long, ProviderAttempt> attemptsByTask = attempts.selectByTaskIds(taskIds).stream()
        .collect(Collectors.toMap(ProviderAttempt::getTaskId, Function.identity(),
            (left, right) -> left));
    Map<Long, ProviderChannel> channelsById = channelMap(attemptsByTask.values());
    ProviderChannel fallback = channels.selectByCode(properties.getChannelCode());
    List<GenerationAdminTaskDTO> views = new ArrayList<>();
    for (GenerationTask row : rows) {
      ProviderAttempt attempt = attemptsByTask.get(row.getId());
      ProviderChannel channel = attempt == null ? fallback
          : channelsById.getOrDefault(attempt.getChannelId(), fallback);
      views.add(GenerationAdminTaskDTO.from(row, attempt, channel, handoffOf(row.getId())));
    }
    return views;
  }

  @Override
  public GenerationAdminTaskDTO task(long taskId) {
    GenerationTask row = tasks.selectById(taskId);
    if (row == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    ProviderAttempt attempt = attempts.selectByTaskId(taskId);
    ProviderChannel channel = attempt == null ? channels.selectByCode(properties.getChannelCode())
        : channels.selectById(attempt.getChannelId());
    return GenerationAdminTaskDTO.from(row, attempt, channel, handoffOf(taskId));
  }

  @Override
  @Transactional
  public GenerationReconcileResultDTO reconcile(long taskId) {
    GenerationTask row = tasks.selectById(taskId);
    if (row == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    ProviderAttempt attempt = attempts.selectByTaskId(taskId);
    if (attempt != null) {
      return new GenerationReconcileResultDTO(taskId, "ATTEMPT",
          attempts.markReconcileNow(attempt.getId()));
    }
    HandoffRow handoff = handoffOf(taskId);
    if (handoff == null) {
      return new GenerationReconcileResultDTO(taskId, "NONE", 0);
    }
    return new GenerationReconcileResultDTO(taskId, "HANDOFF",
        handoffs.wakeWaiting(handoff.getHandoffId()));
  }

  @Override
  public GenerationAdminDeliveryDTO delivery(long taskId) {
    GenerationTask row = requireTask(taskId);
    List<GenerationResult> rows = results.selectByTaskId(taskId);
    List<GenerationAdminResultDTO> views = new ArrayList<>();
    for (GenerationResult result : rows) {
      views.add(GenerationAdminResultDTO.from(result));
    }
    int outputCount = row.getOutputCount() == null ? 0 : row.getOutputCount();
    return new GenerationAdminDeliveryDTO(taskId, row.getState(), row.getAccountingState(),
        outputCount, savedCount(rows), views,
        GenerationCancelDTO.from(cancels.selectByTaskId(taskId)),
        GenerationSettlementDTO.from(settlements.selectByTaskId(taskId)));
  }

  @Override
  @Transactional
  public GenerationAdminActionResultDTO retryResult(long taskId) {
    GenerationTask row = requireTask(taskId);
    if (!"FROZEN".equals(row.getAccountingState())) {
      return new GenerationAdminActionResultDTO(taskId, "RETRY_RESULT", "ALREADY_SETTLED",
          false);
    }
    if (!GenerationTaskState.RESULT_PENDING.equals(row.getState())
        && !GenerationTaskState.REVIEW.equals(row.getState())) {
      return new GenerationAdminActionResultDTO(taskId, "RETRY_RESULT", "NOT_DELIVERY_STATE",
          false);
    }
    boolean applied = resultTransactions.resetFailed(taskId);
    return new GenerationAdminActionResultDTO(taskId, "RETRY_RESULT",
        applied ? "RESULT_SAVE_RETRY_SCHEDULED" : "NO_FAILED_RESULT", applied);
  }

  @Override
  @Transactional
  public GenerationAdminActionResultDTO recoverSettlement(long taskId) {
    GenerationTask row = requireTask(taskId);
    GenerationSettlementTransactions.SettlementOutcome outcome =
        settlementTransactions.recover(taskId);
    if (outcome.settled()) {
      return new GenerationAdminActionResultDTO(taskId, "RECOVER_SETTLEMENT", "SETTLED", true);
    }
    String result = "FROZEN".equals(row.getAccountingState()) ? "NO_TRUSTED_FACT"
        : "ALREADY_SETTLED";
    return new GenerationAdminActionResultDTO(taskId, "RECOVER_SETTLEMENT", result, false);
  }

  @Override
  @Transactional
  public GenerationAdminActionResultDTO cancelTask(long taskId, String reason) {
    requireTask(taskId);
    GenerationCancelRequest request = cancelTransactions.requestAsAdmin(taskId, reason);
    String state = request == null ? null : request.getState();
    boolean applied = GenerationCancelState.isOpen(state)
        || GenerationCancelState.RESOLVED.equals(state);
    return new GenerationAdminActionResultDTO(taskId, "CANCEL_REQUEST",
        state == null ? "UNAVAILABLE" : state, applied);
  }

  private GenerationTask requireTask(long taskId) {
    GenerationTask row = tasks.selectById(taskId);
    if (row == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return row;
  }

  private int savedCount(List<GenerationResult> rows) {
    int saved = 0;
    for (GenerationResult row : rows) {
      if (GenerationResultState.SAVED.equals(row.getState())
          || GenerationResultState.DELIVERED.equals(row.getState())) {
        saved++;
      }
    }
    return saved;
  }

  @Override
  public GenerationUserSchedulingDTO userScheduling(long userId) {
    requireUser(userId);
    return schedulingView(userId);
  }

  @Override
  @Transactional
  public GenerationUserSchedulingDTO updateUserScheduling(long userId,
      GenerationUserSchedulingForm form) {
    requireUser(userId);
    quotas.upsertScheduling(userId, form.getActiveLimit(), form.getScheduleWeight());
    return schedulingView(userId);
  }

  private void requireUser(long userId) {
    if (users.selectById(userId) == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
  }

  private GenerationUserSchedulingDTO schedulingView(long userId) {
    SchedulingRow row = quotas.selectScheduling(userId);
    int activeTasks = row == null ? 0 : row.getActiveTasks();
    Integer activeLimit = row == null ? null : row.getActiveLimit();
    Integer scheduleWeight = row == null ? null : row.getScheduleWeight();
    return new GenerationUserSchedulingDTO(userId, activeTasks, activeLimit, scheduleWeight,
        activeLimit == null ? properties.getUserActiveLimit() : activeLimit,
        scheduleWeight == null ? properties.getDefaultScheduleWeight() : scheduleWeight);
  }

  @Override
  public void addSimulatorFault(SimulatorFaultForm form) {
    // 开关与运行环境双重限制；正式环境即使误开开关也按不存在处理。
    if (!properties.isSimulatorFaultControlEnabled() || !faultControlPolicy.allowed()) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (form == null || form.getMode() == null || !FAULT_MODES.contains(form.getMode())) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    int remaining = form.getRemaining() == null ? 1 : form.getRemaining();
    simulator.insertFault(form.getAttemptKey(), form.getUserId(), form.getApplicationId(),
        form.getMode(), form.getDurationMs(), remaining);
  }

  private HandoffRow handoffOf(long taskId) {
    List<HandoffRow> rows = handoffs.selectByTaskId(taskId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Map<Long, ProviderChannel> channelMap(Iterable<ProviderAttempt> attemptRows) {
    List<Long> ids = new ArrayList<>();
    for (ProviderAttempt attempt : attemptRows) {
      ids.add(attempt.getChannelId());
    }
    if (ids.isEmpty()) {
      return new HashMap<>();
    }
    Map<Long, ProviderChannel> byId = new HashMap<>();
    for (ProviderChannel channel : channels.selectBatchIds(ids)) {
      byId.put(channel.getId(), channel);
    }
    return byId;
  }

  private GenerationAdminSettingsDTO settings() {
    return new GenerationAdminSettingsDTO(properties.isSubmitEnabled(),
        properties.isSchedulingEnabled(), properties.isPollingEnabled(),
        properties.isSimulatorEnabled(), properties.isSimulatorFaultControlEnabled(),
        properties.isSimulatorCallbackEnabled(), properties.getChannelCode(),
        properties.getDispatchBatch(), properties.getPollBatch(), properties.getLeaseSeconds(),
        properties.getSubmitMaxAttempts(), properties.getQueryMaxAttempts(),
        properties.getUserActiveLimit(), properties.getQueueTimeoutSeconds(),
        properties.isFairSchedulingEnabled());
  }
}
