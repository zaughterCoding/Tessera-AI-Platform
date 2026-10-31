package com.aiplatform.generation.service.impl;

import com.aiplatform.catalog.entity.AiApplication;
import com.aiplatform.catalog.mapper.AiApplicationMapper;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.entitlement.GrantSpec;
import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.entitlement.mapper.EntitlementGrantMapper;
import com.aiplatform.entitlement.service.IEntitlementReservationService;
import com.aiplatform.generation.GenerationRequestDigest;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.dto.GenerationStatusDTO;
import com.aiplatform.generation.dto.GenerationTaskDTO;
import com.aiplatform.generation.dto.GenerationTaskForm;
import com.aiplatform.generation.entity.GenerationRequest;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.GenerationQuotaMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import com.aiplatform.generation.service.IGenerationTaskService;
import com.aiplatform.media.service.IMediaService;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提交事务只完成受理、冻结与队列占用；调度在提交后独立进行。 */
@Service
public class GenerationTaskServiceImpl implements IGenerationTaskService {
  private static final int APPLICATION_PUBLISHED = 1;
  private static final int MAX_PROMPT_CHARS = 2000;
  private static final int MAX_NEGATIVE_CHARS = 1000;
  private static final int MAX_MEDIA = 4;
  private static final int MAX_PAGE_SIZE = 20;
  private static final int FROZEN_UNITS = 1;
  private static final String ACCOUNTING_FROZEN = "FROZEN";
  private static final String ENABLED_NOTICE =
      "任务提交与模拟渠道调度已开启；上游执行与结果交付分阶段进行。";
  private static final String DISABLED_NOTICE =
      "当前环境未开启任务提交；既有权益与任务仍可查询。";
  private static final String SCHEDULING_PAUSED_NOTICE =
      "任务提交已开启，但调度当前暂停；任务会排队等待，次数保持冻结。";
  private static final String SIMULATOR_PAUSED_NOTICE =
      "任务提交已开启，但模拟渠道当前关闭；任务会排队等待，次数保持冻结。";

  private final GenerationTaskMapper tasks;
  private final AiApplicationMapper applications;
  private final EntitlementGrantMapper grants;
  private final IMediaService media;
  private final IEntitlementReservationService reservations;
  private final ProviderAttemptMapper attempts;
  private final ProviderChannelMapper channels;
  private final GenerationQuotaMapper quotas;
  private final IBusinessOutboxService outbox;
  private final GenerationProperties properties;

  public GenerationTaskServiceImpl(
      GenerationTaskMapper tasks,
      AiApplicationMapper applications,
      EntitlementGrantMapper grants,
      IMediaService media,
      IEntitlementReservationService reservations,
      ProviderAttemptMapper attempts,
      ProviderChannelMapper channels,
      GenerationQuotaMapper quotas,
      IBusinessOutboxService outbox,
      GenerationProperties properties) {
    this.tasks = tasks;
    this.applications = applications;
    this.grants = grants;
    this.media = media;
    this.reservations = reservations;
    this.attempts = attempts;
    this.channels = channels;
    this.quotas = quotas;
    this.outbox = outbox;
    this.properties = properties;
  }

  @Override
  public GenerationStatusDTO status() {
    ProviderChannel channel = channels.selectByCode(properties.getChannelCode());
    boolean simulated = channel != null && "SIMULATED".equals(channel.getKind());
    boolean scheduling = properties.isSchedulingEnabled() && properties.isSimulatorEnabled();
    String notice;
    if (!properties.isSubmitEnabled()) {
      notice = DISABLED_NOTICE;
    } else if (!properties.isSchedulingEnabled()) {
      notice = SCHEDULING_PAUSED_NOTICE;
    } else if (!properties.isSimulatorEnabled()) {
      notice = SIMULATOR_PAUSED_NOTICE;
    } else {
      notice = ENABLED_NOTICE;
    }
    return new GenerationStatusDTO(properties.isSubmitEnabled(),
        properties.isSchedulingEnabled(), scheduling, simulated,
        channel == null ? null : channel.getCode(),
        channel == null ? null : channel.getDisplayName(), notice);
  }

  @Override
  @Transactional
  public GenerationTaskDTO submit(long userId, GenerationTaskForm form) {
    String requestKey = form.getRequestKey();
    String digest = GenerationRequestDigest.digest(form);
    // 先认领请求键再校验业务状态；已提交任务的重试不受余额、过期或下架影响。
    tasks.insertRequestIfAbsent(userId, requestKey, digest);
    GenerationRequest request = tasks.selectRequestForUpdate(userId, requestKey);
    if (request == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    if (!digest.equals(request.getRequestDigest())) {
      throw new ApiException(ErrorCode.REQUEST_CONFLICT);
    }
    if (request.getTaskId() != null) {
      return detail(userId, request.getTaskId());
    }
    if (!properties.isSubmitEnabled()) {
      throw new ApiException(ErrorCode.SUBMISSION_DISABLED);
    }
    Prepared prepared = prepare(form);
    AiApplication application = applications.selectById(prepared.applicationId());
    if (application == null
        || !Integer.valueOf(APPLICATION_PUBLISHED).equals(application.getStatus())) {
      throw new ApiException(ErrorCode.APPLICATION_UNAVAILABLE);
    }
    // 素材先锁后选包：删除与提交在同一行锁上串行，已受理的输入不会留下悬空引用。
    media.lockOwnedAssets(userId, prepared.mediaIds());
    EntitlementGrant grant = reservations.lockGrantForSubmission(userId, prepared.spec());
    // 队列占用与用户活动任务上限都使用条件更新；任一步失败都会回滚请求、权益与素材锁。
    reserveCapacity(userId);
    GenerationTask task = task(userId, request, digest, prepared, grant);
    tasks.insertTask(task);
    reservations.freezeForTask(grant, task.getId(), FROZEN_UNITS);
    if (!prepared.mediaIds().isEmpty()) {
      tasks.insertTaskMedia(task.getId(), prepared.mediaIds());
    }
    // 交接责任与唤醒消息同事务登记；消息只唤醒调度，执行仍由数据库任务决定。
    tasks.insertHandoff(task.getId());
    outbox.appendEvent(BusinessEventType.GENERATION_DISPATCH.name(), task.getId(), userId);
    if (tasks.bindRequestTask(request.getId(), task.getId()) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    GenerationTask stored = tasks.selectById(task.getId());
    if (stored == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    return GenerationTaskDTO.from(stored, grant, prepared.mediaIds(),
        attempts.selectByTaskId(task.getId()),
        channels.selectByCode(properties.getChannelCode()));
  }

  private void reserveCapacity(long userId) {
    ProviderChannel channel = channels.selectByCode(properties.getChannelCode());
    if (channel == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    if (channels.reserveQueueSlot(properties.getChannelCode()) != 1) {
      throw new ApiException(ErrorCode.QUEUE_FULL);
    }
    quotas.ensureRow(userId);
    if (quotas.tryReserve(userId, properties.getUserActiveLimit()) != 1) {
      throw new ApiException(ErrorCode.TOO_MANY_ACTIVE_TASKS);
    }
  }

  @Override
  public Result<List<GenerationTaskDTO>> list(long userId, int page, int size) {
    int pageNo = Math.max(1, page);
    int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    LambdaQueryWrapper<GenerationTask> query = new LambdaQueryWrapper<>();
    query.eq(GenerationTask::getUserId, userId).orderByDesc(GenerationTask::getId);
    IPage<GenerationTask> result = tasks.selectPage(new Page<>(pageNo, pageSize), query);
    Map<Long, EntitlementGrant> grantsById = new HashMap<>();
    List<Long> grantIds = new ArrayList<>();
    List<Long> taskIds = new ArrayList<>();
    for (GenerationTask task : result.getRecords()) {
      grantIds.add(task.getGrantId());
      taskIds.add(task.getId());
    }
    if (!grantIds.isEmpty()) {
      for (EntitlementGrant grant : grants.selectBatchIds(grantIds)) {
        grantsById.put(grant.getId(), grant);
      }
    }
    Map<Long, ProviderAttempt> attemptsByTask = taskIds.isEmpty()
        ? Collections.emptyMap()
        : attempts.selectByTaskIds(taskIds).stream()
            .collect(Collectors.toMap(ProviderAttempt::getTaskId, Function.identity(),
                (left, right) -> left));
    Map<Long, ProviderChannel> channelsById = channelMap(attemptsByTask.values());
    ProviderChannel fallbackChannel = channels.selectByCode(properties.getChannelCode());
    List<GenerationTaskDTO> views = new ArrayList<>();
    for (GenerationTask task : result.getRecords()) {
      ProviderAttempt attempt = attemptsByTask.get(task.getId());
      ProviderChannel channel = attempt == null ? fallbackChannel
          : channelsById.getOrDefault(attempt.getChannelId(), fallbackChannel);
      views.add(GenerationTaskDTO.from(task, grantsById.get(task.getGrantId()),
          Collections.<Long>emptyList(), attempt, channel));
    }
    return Result.ok(views, result.getTotal());
  }

  @Override
  public GenerationTaskDTO detail(long userId, long taskId) {
    GenerationTask task = tasks.selectById(taskId);
    if (task == null || !Objects.equals(task.getUserId(), userId)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    ProviderAttempt attempt = attempts.selectByTaskId(taskId);
    ProviderChannel channel = attempt == null ? channels.selectByCode(properties.getChannelCode())
        : channels.selectById(attempt.getChannelId());
    return GenerationTaskDTO.from(task, grants.selectById(task.getGrantId()),
        tasks.selectMediaIds(taskId), attempt, channel);
  }

  private Map<Long, ProviderChannel> channelMap(Iterable<ProviderAttempt> attemptRows) {
    List<Long> ids = new ArrayList<>();
    for (ProviderAttempt attempt : attemptRows) {
      ids.add(attempt.getChannelId());
    }
    if (ids.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<Long, ProviderChannel> byId = new HashMap<>();
    for (ProviderChannel channel : channels.selectBatchIds(ids)) {
      byId.put(channel.getId(), channel);
    }
    return byId;
  }

  private Prepared prepare(GenerationTaskForm form) {
    Long applicationId = form.getApplicationId();
    Integer applicationVersion = form.getApplicationVersion();
    Integer outputCount = form.getOutputCount();
    String modelVersion = GenerationRequestDigest.normalizeToken(form.getModelVersion());
    String imageSize = GenerationRequestDigest.normalizeToken(form.getImageSize());
    String prompt = GenerationRequestDigest.normalizeText(form.getPrompt());
    String negative = GenerationRequestDigest.normalizeText(form.getNegativePrompt());
    if (applicationId == null || applicationId <= 0
        || applicationVersion == null || applicationVersion <= 0
        || outputCount == null || outputCount <= 0
        || modelVersion.length() > 64 || imageSize.length() > 32
        || prompt.isEmpty() || prompt.length() > MAX_PROMPT_CHARS
        || negative.length() > MAX_NEGATIVE_CHARS) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    List<Long> mediaIds = form.getMediaIds() == null
        ? Collections.<Long>emptyList() : new ArrayList<>(form.getMediaIds());
    if (mediaIds.size() > MAX_MEDIA) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    return new Prepared(applicationId, applicationVersion, modelVersion, imageSize,
        outputCount, prompt, negative, mediaIds,
        new GrantSpec(applicationId, applicationVersion, modelVersion, imageSize, outputCount));
  }

  private GenerationTask task(long userId, GenerationRequest request, String digest,
      Prepared prepared, EntitlementGrant grant) {
    GenerationTask task = new GenerationTask();
    task.setUserId(userId);
    task.setRequestId(request.getId());
    task.setRequestKey(request.getRequestKey());
    task.setRequestDigest(digest);
    // 执行规格来自购买时的权益包快照，不读取应用或商品的当前配置。
    task.setApplicationId(grant.getApplicationId());
    task.setApplicationName(grant.getApplicationName());
    task.setApplicationVersion(grant.getApplicationVersion());
    task.setModelVersion(grant.getModelVersion());
    task.setImageSize(grant.getImageSize());
    task.setOutputCount(grant.getOutputCount());
    task.setPrompt(prepared.prompt());
    task.setNegativePrompt(prepared.negative());
    task.setGrantId(grant.getId());
    task.setFrozenUnits(FROZEN_UNITS);
    task.setState(GenerationTaskState.QUEUED);
    task.setAccountingState(ACCOUNTING_FROZEN);
    task.setStateNote("");
    return task;
  }

  private record Prepared(
      long applicationId,
      int applicationVersion,
      String modelVersion,
      String imageSize,
      int outputCount,
      String prompt,
      String negative,
      List<Long> mediaIds,
      GrantSpec spec) {
  }
}
