package com.aiplatform.messaging.service.impl;

import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.OutboxRelayTrigger;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class BusinessOutboxServiceImpl implements IBusinessOutboxService {
  private final BusinessOutboxMapper outbox;
  private final OutboxRelayTrigger trigger;

  public BusinessOutboxServiceImpl(BusinessOutboxMapper outbox, OutboxRelayTrigger trigger) {
    this.outbox = outbox;
    this.trigger = trigger;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void appendEvent(String kind, long aggregateId, Long userId) {
    BusinessEventType.from(kind);
    outbox.insertEvent(kind, aggregateId, userId);
    // 只在提交后唤醒，回滚不会产生消息；唤醒失败不影响已提交任务。
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          trigger.wakeUp();
        }
      });
    }
  }
}
