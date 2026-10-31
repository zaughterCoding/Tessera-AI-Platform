package com.aiplatform.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 已受理的生成任务；执行状态与账务冻结状态分开，提交阶段不消费权益。 */
@Data
@TableName("generation_task")
public class GenerationTask {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private Long requestId;
  private String requestKey;
  private String requestDigest;
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
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
