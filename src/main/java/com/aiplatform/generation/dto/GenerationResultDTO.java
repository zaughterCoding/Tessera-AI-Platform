package com.aiplatform.generation.dto;

import com.aiplatform.generation.GenerationResultState;
import com.aiplatform.generation.entity.GenerationResult;
import java.time.LocalDateTime;
import lombok.Data;

/** 用户可见的结果文件状态；上游地址与保存错误不对外暴露。 */
@Data
public class GenerationResultDTO {
  private Long id;
  private Long taskId;
  private Integer position;
  private String state;
  private String contentType;
  private Long sizeBytes;
  private String contentDigest;
  private LocalDateTime savedAt;
  private LocalDateTime deliveredAt;
  private Boolean downloadAvailable;

  public static GenerationResultDTO from(GenerationResult result) {
    GenerationResultDTO view = new GenerationResultDTO();
    view.setId(result.getId());
    view.setTaskId(result.getTaskId());
    view.setPosition(result.getPosition());
    view.setState(result.getState());
    view.setContentType(result.getContentType());
    view.setSizeBytes(result.getSizeBytes());
    view.setContentDigest(result.getContentDigest());
    view.setSavedAt(result.getSavedAt());
    view.setDeliveredAt(result.getDeliveredAt());
    view.setDownloadAvailable(result.getMediaId() != null
        && (GenerationResultState.SAVED.equals(result.getState())
            || GenerationResultState.DELIVERED.equals(result.getState())));
    return view;
  }
}
