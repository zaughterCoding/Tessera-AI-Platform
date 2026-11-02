package com.aiplatform.generation.dto;

/** 用户调度配置；配置值为空表示跟随全局默认值，生效值是实际使用的数字。 */
public record GenerationUserSchedulingDTO(
    long userId,
    int activeTasks,
    Integer activeLimit,
    Integer scheduleWeight,
    int effectiveActiveLimit,
    int effectiveScheduleWeight) {
}
