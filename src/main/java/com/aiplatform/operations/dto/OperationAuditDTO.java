package com.aiplatform.operations.dto;

import com.aiplatform.operations.entity.OperationAudit;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OperationAuditDTO {
  private Long id;
  private String requestId;
  private Long operatorId;
  private String operatorRoles;
  private String action;
  private String objectType;
  private String objectId;
  private String outcome;
  private String errorCode;
  private String detail;
  private LocalDateTime occurredAt;

  public static OperationAuditDTO from(OperationAudit audit) {
    OperationAuditDTO view = new OperationAuditDTO();
    view.setId(audit.getId());
    view.setRequestId(audit.getRequestId());
    view.setOperatorId(audit.getOperatorId());
    view.setOperatorRoles(audit.getOperatorRoles());
    view.setAction(audit.getAction());
    view.setObjectType(audit.getObjectType());
    view.setObjectId(audit.getObjectId());
    view.setOutcome(audit.getOutcome());
    view.setErrorCode(audit.getErrorCode());
    view.setDetail(audit.getDetail());
    view.setOccurredAt(audit.getOccurredAt());
    return view;
  }
}
