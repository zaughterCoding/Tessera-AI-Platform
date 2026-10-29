-- 分析事件的持久投递任务：与订单事实同事务登记，Kafka 确认后才标记已发送。
-- 事件号沿用来源任务身份（order:<业务任务号>），历史补数与实时路径使用同一身份。
CREATE TABLE analytics_outbox (
  event_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NULL,
  action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  occurred_at_ms BIGINT NOT NULL,
  payload TEXT NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  lease_owner VARCHAR(64) NULL,
  lease_until DATETIME(3) NULL,
  sent_at DATETIME(3) NULL,
  last_error VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (event_id),
  KEY idx_analytics_outbox_due (state, next_attempt_at, event_id),
  KEY idx_analytics_outbox_sent (state, sent_at, event_id),
  CONSTRAINT chk_analytics_outbox_state
    CHECK (state IN ('PENDING','SENDING','SENT','REVIEW')),
  CONSTRAINT chk_analytics_outbox_action
    CHECK (action IN ('VISIT','ORDER','PAID','CLAIM','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 已落库的分析明细同时承担去重；去重记录与聚合更新在同一事务提交。
-- occurred_at_ms 是事件发生时间，bucket_ms 是其所属自然分钟桶，窗口只按发生时间归属。
CREATE TABLE analytics_event (
  event_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NULL,
  action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  occurred_at_ms BIGINT NOT NULL,
  bucket_ms BIGINT NOT NULL,
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (event_id),
  KEY idx_analytics_event_time (bucket_ms, application_id),
  KEY idx_analytics_event_retention (occurred_at_ms),
  CONSTRAINT chk_analytics_event_action
    CHECK (action IN ('VISIT','ORDER','PAID','CLAIM','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 每应用每自然分钟的计数；关闭订单单独计数，不计入热度。
CREATE TABLE analytics_application_bucket (
  application_id BIGINT UNSIGNED NOT NULL,
  bucket_ms BIGINT NOT NULL,
  visits BIGINT NOT NULL DEFAULT 0,
  orders BIGINT NOT NULL DEFAULT 0,
  payments BIGINT NOT NULL DEFAULT 0,
  claims BIGINT NOT NULL DEFAULT 0,
  closed_orders BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (application_id, bucket_ms),
  KEY idx_analytics_bucket_time (bucket_ms, application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 登录用户分钟级去重；窗口 UV 对整段窗口做 DISTINCT，不能把每分钟 UV 相加。
CREATE TABLE analytics_visit_user (
  application_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  bucket_ms BIGINT NOT NULL,
  PRIMARY KEY (application_id, user_id, bucket_ms),
  KEY idx_analytics_visit_user_time (bucket_ms, application_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 无法安全归属或内容冲突的消息按 Kafka 位点隔离，不推进真实计数。
CREATE TABLE analytics_failure (
  topic VARCHAR(100) NOT NULL,
  partition_id INT NOT NULL,
  message_offset BIGINT NOT NULL,
  payload MEDIUMTEXT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (topic, partition_id, message_offset)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 历史补数的稳定游标；每批处理与游标推进同事务提交，重启后从原游标继续。
CREATE TABLE analytics_backfill_state (
  id TINYINT UNSIGNED NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'IDLE',
  cursor_event_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
  range_from_ms BIGINT NOT NULL DEFAULT 0,
  processed BIGINT UNSIGNED NOT NULL DEFAULT 0,
  started_at DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  last_error VARCHAR(500) NULL,
  PRIMARY KEY (id),
  CONSTRAINT chk_analytics_backfill_state
    CHECK (state IN ('IDLE','RUNNING','DONE','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO analytics_backfill_state(id) VALUES (1);
