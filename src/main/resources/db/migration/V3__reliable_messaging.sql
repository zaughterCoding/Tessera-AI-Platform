-- 可靠任务与业务写入同事务提交；SENT 只表示发送已确认，DONE 才表示消费完成。
CREATE TABLE business_outbox (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  kind VARCHAR(64) NOT NULL,
  aggregate_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  consume_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deliver_at DATETIME(3) NULL,
  lease_owner VARCHAR(64) NULL,
  lease_until DATETIME(3) NULL,
  sent_at DATETIME(3) NULL,
  consumed_at DATETIME(3) NULL,
  last_error VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_business_outbox_due (state, next_attempt_at, id),
  KEY idx_business_outbox_lease (state, lease_until, id),
  KEY idx_business_outbox_aggregate (kind, aggregate_id, id),
  CONSTRAINT chk_business_outbox_state
    CHECK (state IN ('PENDING','SENDING','SENT','DONE','REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 每个逻辑消费组独立去重；重复投递只能完成同一任务，不能产生第二次业务效果。
CREATE TABLE message_inbox (
  consumer_group VARCHAR(80) NOT NULL,
  event_id BIGINT UNSIGNED NOT NULL,
  processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (consumer_group, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 无法安全归属到持久任务的消息按 Broker 身份隔离，避免影响其他真实任务。
CREATE TABLE message_failure (
  consumer_group VARCHAR(80) NOT NULL,
  event_key VARCHAR(128) NOT NULL,
  event_id BIGINT UNSIGNED NULL,
  broker_message_id VARCHAR(128) NULL,
  payload MEDIUMTEXT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (consumer_group, event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
