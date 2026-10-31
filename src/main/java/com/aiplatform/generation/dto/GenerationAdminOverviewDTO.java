package com.aiplatform.generation.dto;

import java.util.List;
import java.util.Map;

/** 调度概览；只读汇总，不提供任何直接修改任务或余额的入口。 */
public record GenerationAdminOverviewDTO(
    Map<String, Integer> taskStates,
    Map<String, Integer> attemptStates,
    Map<String, Integer> handoffStates,
    Map<String, Integer> resultStates,
    Map<String, Integer> cancelStates,
    List<GenerationChannelDTO> channels,
    int callbackConflicts,
    int simulatedJobs,
    GenerationAdminSettingsDTO settings) {
}
