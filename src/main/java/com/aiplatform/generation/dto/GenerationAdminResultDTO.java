package com.aiplatform.generation.dto;

import com.aiplatform.generation.entity.GenerationResult;
import java.time.LocalDateTime;

/** 管理排障结果视图：包含保存失败原因与上游引用，仅管理端可见。 */
public record GenerationAdminResultDTO(
    long id,
    long taskId,
    long attemptId,
    int position,
    String state,
    String sourceRef,
    String expectedDigest,
    Long mediaId,
    String contentType,
    Long sizeBytes,
    String contentDigest,
    int saveAttempts,
    String lastError,
    LocalDateTime nextAttemptAt,
    LocalDateTime savedAt,
    LocalDateTime deliveredAt,
    LocalDateTime createdAt) {

  public static GenerationAdminResultDTO from(GenerationResult row) {
    return new GenerationAdminResultDTO(row.getId(), row.getTaskId(), row.getAttemptId(),
        row.getPosition() == null ? 0 : row.getPosition(), row.getState(), row.getSourceRef(),
        row.getExpectedDigest(), row.getMediaId(), row.getContentType(), row.getSizeBytes(),
        row.getContentDigest(), row.getSaveAttempts() == null ? 0 : row.getSaveAttempts(),
        row.getLastError(), row.getNextAttemptAt(), row.getSavedAt(), row.getDeliveredAt(),
        row.getCreatedAt());
  }
}
