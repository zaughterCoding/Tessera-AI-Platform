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
import com.aiplatform.generation.dto.GenerationStatusDTO;
import com.aiplatform.generation.dto.GenerationTaskDTO;
import com.aiplatform.generation.dto.GenerationTaskForm;
import com.aiplatform.generation.entity.GenerationRequest;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.service.IGenerationTaskService;
import com.aiplatform.media.service.IMediaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提交事务只完成受理与冻结；不调用上游、不产生结果，也不提前结算。 */
@Service
public class GenerationTaskServiceImpl implements IGenerationTaskService {
  private static final int APPLICATION_PUBLISHED = 1;
  private static final int MAX_PROMPT_CHARS = 2000;
  private static final int MAX_NEGATIVE_CHARS = 1000;
  private static final int MAX_MEDIA = 4;
  private static final int MAX_PAGE_SIZE = 20;
  private static final int FROZEN_UNITS = 1;
  private static final String QUEUED = "QUEUED";
  private static final String ACCOUNTING_FROZEN = "FROZEN";
  private static final String ENABLED_NOTICE =
      "任务提交已开启；提交后只完成排队与次数冻结，AI 执行尚未开始。";
  private static final String DISABLED_NOTICE =
      "当前环境未开启任务提交；既有权益与任务仍可查询。";
  private final GenerationTaskMapper tasks;
  private final AiApplicationMapper applications;
  private final EntitlementGrantMapper grants;
  private final IMediaService media;
  private final IEntitlementReservationService reservations;
  private final boolean submitEnabled;

  public GenerationTaskServiceImpl(
      GenerationTaskMapper tasks,
      AiApplicationMapper applications,
      EntitlementGrantMapper grants,
      IMediaService media,
      IEntitlementReservationService reservations,
      @Value("${app.generation.submit-enabled:false}") boolean submitEnabled) {
    this.tasks = tasks;
    this.applications = applications;
    this.grants = grants;
    this.media = media;
    this.reservations = reservations;
    this.submitEnabled = submitEnabled;
  }

  @Override
  public GenerationStatusDTO status() {
    return new GenerationStatusDTO(submitEnabled, false,
        submitEnabled ? ENABLED_NOTICE : DISABLED_NOTICE);
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
    if (!submitEnabled) {
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
    GenerationTask task = task(userId, request, digest, prepared, grant);
    tasks.insertTask(task);
    reservations.freezeForTask(grant, task.getId(), FROZEN_UNITS);
    if (!prepared.mediaIds().isEmpty()) {
      tasks.insertTaskMedia(task.getId(), prepared.mediaIds());
    }
    // 交接责任随任务同事务登记；本阶段没有消费者，由 N03 按任务身份接管。
    tasks.insertHandoff(task.getId());
    if (tasks.bindRequestTask(request.getId(), task.getId()) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    GenerationTask stored = tasks.selectById(task.getId());
    if (stored == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    return GenerationTaskDTO.from(stored, grant, prepared.mediaIds());
  }

  @Override
  public Result<List<GenerationTaskDTO>> list(long userId, int page, int size) {
    int pageNo = Math.max(1, page);
    int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    LambdaQueryWrapper<GenerationTask> query = new LambdaQueryWrapper<>();
    query.eq(GenerationTask::getUserId, userId).orderByDesc(GenerationTask::getId);
    IPage<GenerationTask> result = tasks.selectPage(new Page<>(pageNo, pageSize), query);
    Map<Long, EntitlementGrant> byId = new HashMap<>();
    List<Long> grantIds = new ArrayList<>();
    for (GenerationTask task : result.getRecords()) {
      grantIds.add(task.getGrantId());
    }
    if (!grantIds.isEmpty()) {
      for (EntitlementGrant grant : grants.selectBatchIds(grantIds)) {
        byId.put(grant.getId(), grant);
      }
    }
    List<GenerationTaskDTO> views = new ArrayList<>();
    for (GenerationTask task : result.getRecords()) {
      views.add(GenerationTaskDTO.from(task, byId.get(task.getGrantId()),
          Collections.<Long>emptyList()));
    }
    return Result.ok(views, result.getTotal());
  }

  @Override
  public GenerationTaskDTO detail(long userId, long taskId) {
    GenerationTask task = tasks.selectById(taskId);
    if (task == null || !Objects.equals(task.getUserId(), userId)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return GenerationTaskDTO.from(task, grants.selectById(task.getGrantId()),
        tasks.selectMediaIds(taskId));
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
    task.setState(QUEUED);
    task.setAccountingState(ACCOUNTING_FROZEN);
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
