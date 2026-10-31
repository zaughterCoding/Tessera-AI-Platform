package com.aiplatform.generation.dto;

import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 任务视图报告真实调度阶段；结果只标记待交付，不提供下载、取消或结算入口。 */
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
  private LocalDateTime createdAt;
  private String executionNotice;

  public static GenerationTaskDTO from(GenerationTask task, EntitlementGrant grant,
      List<Long> mediaIds) {
    return from(task, grant, mediaIds, null, null);
  }

  public static GenerationTaskDTO from(GenerationTask task, EntitlementGrant grant,
      List<Long> mediaIds, ProviderAttempt attempt, ProviderChannel channel) {
    GenerationTaskDTO view = new GenerationTaskDTO();
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
    view.setStateNote(task.getStateNote());
    view.setAttemptState(attempt == null ? null : attempt.getState());
    if (channel != null) {
      view.setChannelCode(channel.getCode());
      view.setChannelDisplayName(channel.getDisplayName());
      view.setSimulatedChannel("SIMULATED".equals(channel.getKind()));
    }
    view.setGrantStatus(grant == null ? null : grant.getStatus());
    view.setGrantExpiresAt(grant == null ? null : grant.getExpiresAt());
    view.setMediaIds(mediaIds);
    view.setCreatedAt(task.getCreatedAt());
    view.setExecutionNotice(noticeFor(task.getState(), view.getSimulatedChannel()));
    return view;
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
      default -> "任务状态以服务端记录为准。";
    };
  }
}
