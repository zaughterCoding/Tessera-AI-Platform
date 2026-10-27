package com.aiplatform.messaging.service;

import com.aiplatform.messaging.entity.BusinessOutboxTask;

public interface IBusinessProjectionService {
  /** 只按数据库中的可信任务执行投影；失败时抛出以便可靠重试。 */
  void project(BusinessOutboxTask task);
}
