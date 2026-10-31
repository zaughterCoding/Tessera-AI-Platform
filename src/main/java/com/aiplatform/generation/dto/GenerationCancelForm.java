package com.aiplatform.generation.dto;

import lombok.Data;

/** 取消原因只用于说明，不参与权限判断；服务端会再次校验任务归属与状态。 */
@Data
public class GenerationCancelForm {
  private String reason;
}
