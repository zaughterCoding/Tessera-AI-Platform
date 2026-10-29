package com.aiplatform.operations.service;

import com.aiplatform.common.api.Result;
import com.aiplatform.operations.dto.OperationAuditDTO;
import com.aiplatform.operations.entity.OperationAudit;
import java.util.List;

public interface IOperationAuditService {
  /** 独立事务写入审计事实；失败不影响已完成的业务结果。 */
  void record(OperationAudit audit);

  Result<List<OperationAuditDTO>> list(String action, String objectType, Long operatorId,
      String outcome, int limit, int offset);
}
