package com.aiplatform.messaging.service;

import com.aiplatform.messaging.entity.BusinessOutboxTask;
import java.util.List;

public interface IOutboxRelayService {
  void relayOutboxBatch();

  void redriveUnconsumedEvents();

  List<BusinessOutboxTask> listTasks(String state, int limit, int offset);

  BusinessOutboxTask getTask(long id);

  /** 只允许 REVIEW 任务重放；任务不存在或状态不符时返回 false。 */
  boolean retryReview(long id);

  long countByState(String state);
}
