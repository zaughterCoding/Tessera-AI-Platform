package com.aiplatform.messaging.service;

public interface IBusinessOutboxService {
  /** 必须在业务事务内调用；事务回滚时任务不会留下。 */
  void appendEvent(String kind, long aggregateId, Long userId);
}
