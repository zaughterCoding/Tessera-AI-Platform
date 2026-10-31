package com.aiplatform.generation.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.config.SimulatorFaultControlPolicy;
import com.aiplatform.generation.dto.GenerationAdminOverviewDTO;
import com.aiplatform.generation.dto.GenerationAdminSettingsDTO;
import com.aiplatform.generation.dto.GenerationAdminTaskDTO;
import com.aiplatform.generation.dto.GenerationChannelDTO;
import com.aiplatform.generation.dto.GenerationReconcileResultDTO;
import com.aiplatform.generation.dto.SimulatorFaultForm;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.GenerationHandoffMapper;
import com.aiplatform.generation.mapper.GenerationHandoffMapper.HandoffRow;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.mapper.ProviderCallbackMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import com.aiplatform.generation.mapper.SimulatedProviderMapper;
import com.aiplatform.generation.service.IGenerationAdminService;
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
      GenerationTaskState.QUEUE_TIMEOUT, GenerationTaskState.REVIEW);
  private static final List<String> TASK_ORDER = List.of(
      GenerationTaskState.QUEUED, GenerationTaskState.DISPATCHING,
      GenerationTaskState.SUBMITTED, GenerationTaskState.RUNNING,
      GenerationTaskState.RECONCILING, GenerationTaskState.RESULT_PENDING,
      GenerationTaskState.FAILED_PENDING_FOLLOWUP,
      GenerationTaskState.CANCELLED_PENDING_FOLLOWUP,
      GenerationTaskState.QUEUE_TIMEOUT, GenerationTaskState.REVIEW);
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
  private final GenerationProperties properties;
  private final SimulatorFaultControlPolicy faultControlPolicy;

  public GenerationAdminServiceImpl(GenerationTaskMapper tasks, ProviderAttemptMapper attempts,
      ProviderChannelMapper channels, GenerationHandoffMapper handoffs,
      ProviderCallbackMapper callbacks, SimulatedProviderMapper simulator,
      GenerationProperties properties, SimulatorFaultControlPolicy faultControlPolicy) {
    this.tasks = tasks;
    this.attempts = attempts;
    this.channels = channels;
    this.handoffs = handoffs;
    this.callbacks = callbacks;
    this.simulator = simulator;
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
    List<GenerationChannelDTO> channelViews = new ArrayList<>();
    for (ProviderChannel channel : channels.selectList(null)) {
      channelViews.add(GenerationChannelDTO.from(channel));
    }
    return new GenerationAdminOverviewDTO(taskCounts, attemptCounts, handoffCounts, channelViews,
        callbacks.countConflicts(), simulator.countJobs(), settings());
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
        properties.getUserActiveLimit(), properties.getQueueTimeoutSeconds());
  }
}
