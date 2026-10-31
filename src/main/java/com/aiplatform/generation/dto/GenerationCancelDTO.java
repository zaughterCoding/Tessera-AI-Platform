package com.aiplatform.generation.dto;

import com.aiplatform.generation.GenerationCancelState;
import com.aiplatform.generation.entity.GenerationCancelRequest;
import java.time.LocalDateTime;
import lombok.Data;

/** 取消请求用户视图；最终是否可取消以服务端记录为准。 */
@Data
public class GenerationCancelDTO {
  private Long id;
  private Long taskId;
  private String state;
  private String outcome;
  private String reason;
  private LocalDateTime requestedAt;
  private LocalDateTime resolvedAt;
  private Boolean open;

  public static GenerationCancelDTO from(GenerationCancelRequest request) {
    if (request == null) {
      return null;
    }
    GenerationCancelDTO view = new GenerationCancelDTO();
    view.setId(request.getId());
    view.setTaskId(request.getTaskId());
    view.setState(request.getState());
    view.setOutcome(request.getOutcome());
    view.setReason(request.getReason());
    view.setRequestedAt(request.getRequestedAt());
    view.setResolvedAt(request.getResolvedAt());
    view.setOpen(GenerationCancelState.isOpen(request.getState()));
    return view;
  }
}
