package com.aiplatform.generation.dto;

/** 公开可见的提交开关；不暴露内部配置、额度或执行渠道。 */
public record GenerationStatusDTO(boolean submitEnabled, boolean executionStarted,
    String notice) {
}
