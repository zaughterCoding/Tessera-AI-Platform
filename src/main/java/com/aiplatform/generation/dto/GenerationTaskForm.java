package com.aiplatform.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Data;

/** 提交请求只表达用户意图；次数、归属、任务状态和执行规格由服务端权益快照确定。 */
@Data
public class GenerationTaskForm {
  @NotBlank
  @Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
  private String requestKey;

  private Long applicationId;
  private Integer applicationVersion;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
  private String prompt;
  private String negativePrompt;
  private List<Long> mediaIds;
}
