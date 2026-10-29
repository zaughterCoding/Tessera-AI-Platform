package com.aiplatform.operations.service.impl;

import com.aiplatform.common.api.Result;
import com.aiplatform.operations.dto.OperationAuditDTO;
import com.aiplatform.operations.entity.OperationAudit;
import com.aiplatform.operations.mapper.OperationAuditMapper;
import com.aiplatform.operations.service.IOperationAuditService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationAuditServiceImpl implements IOperationAuditService {
  private final OperationAuditMapper audits;

  public OperationAuditServiceImpl(OperationAuditMapper audits) {
    this.audits = audits;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(OperationAudit audit) {
    audits.insert(audit);
  }

  @Override
  public Result<List<OperationAuditDTO>> list(String action, String objectType, Long operatorId,
      String outcome, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 200));
    int safeOffset = Math.max(0, offset);
    List<OperationAuditDTO> views = new ArrayList<>();
    for (OperationAudit audit : audits.selectAudits(action, objectType, operatorId, outcome,
        safeLimit, safeOffset)) {
      views.add(OperationAuditDTO.from(audit));
    }
    return Result.ok(views, audits.countAudits(action, objectType, operatorId, outcome));
  }
}
