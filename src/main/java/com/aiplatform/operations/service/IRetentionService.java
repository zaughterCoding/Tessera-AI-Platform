package com.aiplatform.operations.service;

import com.aiplatform.operations.dto.RetentionOverviewDTO;
import com.aiplatform.operations.dto.RetentionRunDTO;
import com.aiplatform.operations.retention.RetentionTarget;

public interface IRetentionService {
  RetentionOverviewDTO overview();

  /** 管理入口：与定时清理共用同一实现，额外执行 ADMIN 方法校验。 */
  RetentionRunDTO runAsAdmin(RetentionTarget target, int maxBatches);

  /** 定时任务等内部调用入口，不附加 HTTP 管理权限。 */
  RetentionRunDTO run(RetentionTarget target, int maxBatches);
}
