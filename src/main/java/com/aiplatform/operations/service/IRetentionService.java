package com.aiplatform.operations.service;

import com.aiplatform.operations.dto.RetentionOverviewDTO;
import com.aiplatform.operations.dto.RetentionRunDTO;
import com.aiplatform.operations.retention.RetentionTarget;

public interface IRetentionService {
  RetentionOverviewDTO overview();

  RetentionRunDTO run(RetentionTarget target, int maxBatches);
}
