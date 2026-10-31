package com.aiplatform.generation.dto;

import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.generation.entity.GenerationTask;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 任务视图只报告已受理与已冻结事实，不虚构执行进度、结果或结算状态。 */
@Data
public class GenerationTaskDTO {
  public static final String EXECUTION_NOTICE =
      "任务已受理，AI 执行尚未开始；当前阶段不提供结果、取消或结算。";

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
  private String grantStatus;
  private LocalDateTime grantExpiresAt;
  private List<Long> mediaIds;
  private LocalDateTime createdAt;
  private String executionNotice;

  public static GenerationTaskDTO from(GenerationTask task, EntitlementGrant grant,
      List<Long> mediaIds) {
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
    view.setGrantStatus(grant == null ? null : grant.getStatus());
    view.setGrantExpiresAt(grant == null ? null : grant.getExpiresAt());
    view.setMediaIds(mediaIds);
    view.setCreatedAt(task.getCreatedAt());
    view.setExecutionNotice(EXECUTION_NOTICE);
    return view;
  }
}
