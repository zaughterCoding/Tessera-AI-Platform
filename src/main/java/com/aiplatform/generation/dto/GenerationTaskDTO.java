package com.aiplatform.generation.dto;

import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.entitlement.entity.GenerationSettlement;
import com.aiplatform.generation.GenerationCancelState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderResultMetadata;
import com.aiplatform.generation.entity.GenerationCancelRequest;
import com.aiplatform.generation.entity.GenerationResult;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 任务视图报告真实调度阶段；结果、取消与结算入口均由服务层登记事实驱动。 */
@Data
public class GenerationTaskDTO {
  private Long id;
  private Long applicationId;
  private String applicationName;
  private Integer applicationVersion;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
  private String prompt;
  private String negativePrompt;
  private Long grantId;
  private Integer frozenUnits;
  private String state;
  private String accountingState;
  private String stateNote;
  private String attemptState;
  private String channelCode;
  private String channelDisplayName;
  private Boolean simulatedChannel;
  private String grantStatus;
  private LocalDateTime grantExpiresAt;
  private List<Long> mediaIds;
  private List<GenerationResultDTO> results = new ArrayList<>();
  private GenerationCancelDTO cancelRequest;
  private GenerationSettlementDTO settlement;
  private Boolean canCancel;
  private LocalDateTime deliveredAt;
  private LocalDateTime settledAt;
  private LocalDateTime createdAt;
  private String executionNotice;

  private static final String INCOMPLETE_RESULT_NOTE =
      "上游已返回成功，但结果元数据不完整；系统将继续核对，冻结次数保留";
  private static final String INCOMPLETE_RESULT_NOTICE =
      "上游已返回成功，但结果元数据不完整，系统会继续核对；当前不能下载或消费次数。";

  public static GenerationTaskDTO from(GenerationTask task, EntitlementGrant grant,
      List<Long> mediaIds) {
    return from(task, grant, mediaIds, null, null);
  }

  public static GenerationTaskDTO from(GenerationTask task, EntitlementGrant grant,
      List<Long> mediaIds, ProviderAttempt attempt, ProviderChannel channel) {
    return from(task, grant, mediaIds, attempt, channel, 0);
  }

  public static GenerationTaskDTO from(GenerationTask task, EntitlementGrant grant,
      List<Long> mediaIds, ProviderAttempt attempt, ProviderChannel channel,
      int savedResultCount) {
    GenerationTaskDTO view = new GenerationTaskDTO();
    int outputCount = task.getOutputCount() == null ? 0 : task.getOutputCount();
    boolean resultsComplete = outputCount > 0 && savedResultCount >= outputCount;
    view.setId(task.getId());
    view.setApplicationId(task.getApplicationId());
    view.setApplicationName(task.getApplicationName());
    view.setApplicationVersion(task.getApplicationVersion());
    view.setModelVersion(task.getModelVersion());
    view.setImageSize(task.getImageSize());
    view.setOutputCount(task.getOutputCount());
    view.setPrompt(task.getPrompt());
    view.setNegativePrompt(task.getNegativePrompt());
    view.setGrantId(task.getGrantId());
    view.setFrozenUnits(task.getFrozenUnits());
    view.setState(task.getState());
    view.setAccountingState(task.getAccountingState());
    view.setStateNote(stateNoteFor(task.getState(), task.getStateNote(), attempt,
        resultsComplete));
    view.setAttemptState(attempt == null ? null : attempt.getState());
    if (channel != null) {
      view.setChannelCode(channel.getCode());
      view.setChannelDisplayName(channel.getDisplayName());
      view.setSimulatedChannel("SIMULATED".equals(channel.getKind()));
    }
    view.setGrantStatus(grant == null ? null : grant.getStatus());
    view.setGrantExpiresAt(grant == null ? null : grant.getExpiresAt());
    view.setMediaIds(mediaIds);
    view.setDeliveredAt(task.getDeliveredAt());
    view.setSettledAt(task.getSettledAt());
    view.setCreatedAt(task.getCreatedAt());
    boolean incompleteResult =
        !resultsComplete && ProviderResultMetadata.successMetadataMissing(task.getState(),
            attempt);
    view.setExecutionNotice(incompleteResult ? INCOMPLETE_RESULT_NOTICE
        : noticeFor(task.getState(), view.getSimulatedChannel()));
    return view;
  }

  /** 历史成功快照缺少结果元数据时，展示说明必须如实报告不完整，不能沿用旧文案。 */
  public static String stateNoteFor(String state, String storedNote, ProviderAttempt attempt) {
    return stateNoteFor(state, storedNote, attempt, false);
  }

  public static String stateNoteFor(String state, String storedNote, ProviderAttempt attempt,
      boolean resultsComplete) {
    return !resultsComplete && ProviderResultMetadata.successMetadataMissing(state, attempt)
        ? INCOMPLETE_RESULT_NOTE : storedNote;
  }

  /** 交付视图只由服务层登记事实生成；取消入口与结算说明都随任务返回。 */
  public void attachDelivery(List<GenerationResult> resultRows,
      GenerationCancelRequest cancel, GenerationSettlement settlementRow) {
    List<GenerationResultDTO> views = new ArrayList<>();
    for (GenerationResult row : resultRows) {
      views.add(GenerationResultDTO.from(row));
    }
    this.setResults(views);
    this.setCancelRequest(GenerationCancelDTO.from(cancel));
    this.setSettlement(GenerationSettlementDTO.from(settlementRow));
    this.setCanCancel(canCancel(state, accountingState, cancel));
  }

  private static boolean canCancel(String state, String accounting,
      GenerationCancelRequest cancel) {
    if (!"FROZEN".equals(accounting) || (cancel != null
        && GenerationCancelState.isOpen(cancel.getState()))) {
      return false;
    }
    return !GenerationTaskState.isSettledFinal(state)
        && !GenerationTaskState.RESULT_PENDING.equals(state)
        && !GenerationTaskState.FAILED_PENDING_FOLLOWUP.equals(state)
        && !GenerationTaskState.CANCELLED_PENDING_FOLLOWUP.equals(state)
        && !GenerationTaskState.QUEUE_TIMEOUT.equals(state);
  }

  public static String noticeFor(String state, Boolean simulated) {
    String prefix = Boolean.TRUE.equals(simulated) ? "模拟渠道：" : "";
    return prefix + switch (state == null ? "" : state) {
      case GenerationTaskState.QUEUED ->
          "任务已排队等待调度，次数保持冻结；本页不自动轮询，可刷新查看进展。";
      case GenerationTaskState.DISPATCHING ->
          "调度器已认领任务，正在向上游提交；次数保持冻结。";
      case GenerationTaskState.SUBMITTED ->
          "上游已受理，等待开始执行；次数保持冻结。";
      case GenerationTaskState.RUNNING ->
          "上游正在执行；结果尚未交付，次数保持冻结。";
      case GenerationTaskState.RECONCILING ->
          "上游状态待核对，系统会继续查询；次数保持冻结，不会自动退款。";
      case GenerationTaskState.RESULT_PENDING ->
          "上游已返回成功与结果元数据，等待后续交付与结算；当前不能下载或消费次数。";
      case GenerationTaskState.FAILED_PENDING_FOLLOWUP ->
          "上游确认失败，等待后续处理；冻结次数不会自动释放。";
      case GenerationTaskState.CANCELLED_PENDING_FOLLOWUP ->
          "上游已取消，等待后续处理；冻结次数不会自动释放。";
      case GenerationTaskState.QUEUE_TIMEOUT ->
          "排队超过时限，任务转入待处理；冻结次数保留，需后续处理。";
      case GenerationTaskState.REVIEW ->
          "上游状态无法确认，已转入人工核对；冻结次数保留。";
      case GenerationTaskState.CANCEL_REQUESTED ->
          "已登记取消请求，等待上游确认；确认前冻结次数保留。";
      case GenerationTaskState.SUCCEEDED ->
          "结果文件已保存并验证，任务完成；本次次数已消费。";
      case GenerationTaskState.FAILED ->
          "上游确认失败，任务结束；冻结次数已按权益包状态释放。";
      case GenerationTaskState.CANCELLED ->
          "任务已取消，未产生可用结果；冻结次数已按权益包状态释放。";
      default -> "任务状态以服务端记录为准。";
    };
  }
}
