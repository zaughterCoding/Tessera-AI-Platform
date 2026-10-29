package com.aiplatform.operations.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("operation_audit")
public class OperationAudit {
  @TableId(type = IdType.AUTO)
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
}
