package com.aiplatform.generation.dto;

import java.util.List;

/** 任务交付与结算的只读快照；重试和恢复动作仍由服务层重新校验事实。 */
public record GenerationAdminDeliveryDTO(
    long taskId,
    String taskState,
    String accountingState,
    int outputCount,
    int savedCount,
    List<GenerationAdminResultDTO> results,
    GenerationCancelDTO cancelRequest,
    GenerationSettlementDTO settlement) {
}
