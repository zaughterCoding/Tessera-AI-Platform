package com.aiplatform.generation.dto;

/** 管理动作结果；applied=false 表示现有事实不满足条件，未修改任何账务。 */
public record GenerationAdminActionResultDTO(long taskId, String action, String outcome,
    boolean applied) {
}
