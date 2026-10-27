package com.aiplatform.messaging.service;

import java.time.LocalDateTime;

public interface IBusinessOutboxService {
  /** 必须在业务事务内调用；事务回滚时任务不会留下。 */
  void appendEvent(String kind, long aggregateId, Long userId);

  /** 在业务事务内登记到期后才可投递的任务；时间使用数据库 UTC 时间。 */
  void appendEventAt(String kind, long aggregateId, Long userId, LocalDateTime deliverAt);
}
