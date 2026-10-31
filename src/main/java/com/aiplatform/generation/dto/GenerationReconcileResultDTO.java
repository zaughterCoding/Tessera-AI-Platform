package com.aiplatform.generation.dto;

/** 对账恢复只提前下一次检查时间；不改变状态、余额或容量计数。 */
public record GenerationReconcileResultDTO(long taskId, String target, int changed) {
}
