-- 上游渠道的稳定容量行：in_flight 表示已占用的上游在途预算，queued 表示已受理但尚未交接的队列占用。
-- 两个计数只通过条件更新维护；调低上限只阻止新的抢占，不会改动既有在途事实。
CREATE TABLE provider_channel (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  display_name VARCHAR(64) NOT NULL,
  kind VARCHAR(16) NOT NULL DEFAULT 'SIMULATED',
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  max_in_flight INT UNSIGNED NOT NULL,
  in_flight INT UNSIGNED NOT NULL DEFAULT 0,
  queue_capacity INT UNSIGNED NOT NULL,
  queued INT UNSIGNED NOT NULL DEFAULT 0,
  queue_timeout_seconds INT UNSIGNED NOT NULL,
  supports_idempotent_submit TINYINT(1) NOT NULL,
  supports_query_by_attempt TINYINT(1) NOT NULL,
  supports_cancel TINYINT(1) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_channel_code (code),
  CONSTRAINT chk_provider_channel_kind CHECK (kind IN ('SIMULATED')),
  CONSTRAINT chk_provider_channel_status CHECK (status IN ('ENABLED','DISABLED')),
  CONSTRAINT chk_provider_channel_limits CHECK (
    max_in_flight > 0 AND queue_capacity > 0 AND queue_timeout_seconds > 0
  ),
  CONSTRAINT chk_provider_channel_simulated CHECK (
    kind <> 'SIMULATED'
    OR (supports_idempotent_submit = 1 AND supports_query_by_attempt = 1)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认模拟渠道支持幂等提交、按客户端尝试身份查询与取消；另一个不可取消渠道用于验证能力差异。
INSERT INTO provider_channel(
  code, display_name, kind, status, max_in_flight, in_flight, queue_capacity, queued,
  queue_timeout_seconds, supports_idempotent_submit, supports_query_by_attempt, supports_cancel
) VALUES
  ('simulated-primary', '模拟渠道（支持取消）', 'SIMULATED', 'ENABLED', 4, 0, 200, 0,
   1800, 1, 1, 1),
  ('simulated-no-cancel', '模拟渠道（不支持取消）', 'SIMULATED', 'DISABLED', 2, 0, 20, 0,
   1800, 1, 1, 0);

-- 每个任务最多一个稳定上游尝试；attempt_key 在首次提交前持久化，重试和接管始终复用同一身份。
CREATE TABLE provider_attempt (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  channel_id BIGINT UNSIGNED NOT NULL,
  attempt_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  provider_task_id VARCHAR(128) NULL,
  state VARCHAR(24) NOT NULL,
  owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  lease_until DATETIME(3) NULL,
  generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
  submit_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  query_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NULL,
  submitted_at DATETIME(3) NULL,
  last_contact_at DATETIME(3) NULL,
  terminal_at DATETIME(3) NULL,
  result_ref VARCHAR(255) NULL,
  result_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  failure_code VARCHAR(48) NULL,
  failure_detail VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_attempt_task (task_id),
  UNIQUE KEY uk_provider_attempt_key (attempt_key),
  KEY idx_provider_attempt_due (state, next_attempt_at, id),
  KEY idx_provider_attempt_lease (state, lease_until, id),
  KEY idx_provider_attempt_channel (channel_id, state, id),
  CONSTRAINT fk_provider_attempt_task FOREIGN KEY (task_id) REFERENCES generation_task (id),
  CONSTRAINT fk_provider_attempt_channel FOREIGN KEY (channel_id) REFERENCES provider_channel (id),
  CONSTRAINT chk_provider_attempt_state CHECK (state IN (
    'SUBMIT_PENDING','SUBMITTING','ACCEPTED','RUNNING','RECONCILING','RESULT_PENDING',
    'FAILED','CANCELLED','REVIEW'
  ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 回调收件箱只保存已验证事件的稳定身份与摘要；业务事实在同一事务内更新。
CREATE TABLE provider_callback_inbox (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  channel_id BIGINT UNSIGNED NOT NULL,
  event_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  event_type VARCHAR(24) NOT NULL,
  attempt_id BIGINT UNSIGNED NOT NULL,
  payload_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_size INT UNSIGNED NOT NULL,
  outcome VARCHAR(16) NOT NULL,
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_callback_event (channel_id, event_key),
  KEY idx_provider_callback_attempt (attempt_id, id),
  CONSTRAINT fk_provider_callback_channel FOREIGN KEY (channel_id)
    REFERENCES provider_channel (id),
  CONSTRAINT fk_provider_callback_attempt FOREIGN KEY (attempt_id)
    REFERENCES provider_attempt (id),
  CONSTRAINT chk_provider_callback_outcome CHECK (outcome IN ('APPLIED','DUPLICATE','IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 同身份不同内容不覆盖原事件，单独隔离并保留双方摘要，等待人工核对。
CREATE TABLE provider_callback_conflict (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  channel_id BIGINT UNSIGNED NOT NULL,
  event_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  existing_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  incoming_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_size INT UNSIGNED NOT NULL,
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_provider_callback_conflict (channel_id, event_key, incoming_digest),
  CONSTRAINT fk_provider_callback_conflict_channel FOREIGN KEY (channel_id)
    REFERENCES provider_channel (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模拟上游拥有独立持久状态：它不参与业务提交事务，也没有指向业务表的外键，
-- 因此应用停止、请求超时或事务回滚都不会让上游任务消失。
CREATE TABLE simulated_provider_job (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  attempt_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  provider_task_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
  fault_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
  duration_ms INT UNSIGNED NOT NULL,
  complete_at DATETIME(3) NOT NULL,
  query_failures_remaining INT UNSIGNED NOT NULL DEFAULT 0,
  result_ref VARCHAR(255) NULL,
  result_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  failure_code VARCHAR(48) NULL,
  failure_detail VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  accepted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  terminal_at DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_simulated_provider_attempt (attempt_key),
  UNIQUE KEY uk_simulated_provider_task (provider_task_id),
  KEY idx_simulated_provider_complete (state, complete_at, id),
  CONSTRAINT chk_simulated_provider_state CHECK (
    state IN ('RUNNING','SUCCEEDED','FAILED','CANCELLED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 故障规则只影响模拟渠道的下一次匹配；明文密钥不进入本表。
CREATE TABLE simulated_provider_fault (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  match_attempt_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  match_user_id BIGINT UNSIGNED NULL,
  match_application_id BIGINT UNSIGNED NULL,
  mode VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  duration_ms INT UNSIGNED NULL,
  remaining INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_simulated_provider_fault_match (match_attempt_key, match_user_id, id),
  CONSTRAINT chk_simulated_provider_fault_mode CHECK (mode IN (
    'REJECT_SUBMIT','LOSE_SUBMIT_RESPONSE','TIMEOUT_BUT_RUNNING','SLOW','FAIL',
    'QUERY_TEMPORARY_FAILURE'
  )),
  CONSTRAINT chk_simulated_provider_fault_remaining CHECK (remaining > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户活动任务上限使用稳定行加条件更新，禁止先 COUNT 再插入。
CREATE TABLE generation_user_quota (
  user_id BIGINT UNSIGNED NOT NULL,
  active_tasks INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_generation_user_quota_user FOREIGN KEY (user_id) REFERENCES creator_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 既有任务已被受理并冻结，按用户回填活动任务数；超出新上限的存量任务不回滚、不丢弃。
INSERT INTO generation_user_quota(user_id, active_tasks)
SELECT user_id, COUNT(*) FROM generation_task WHERE state = 'QUEUED' GROUP BY user_id;

-- 旧交接行的队列占用归入默认渠道；只登记占用，不改变任务、冻结或账务事实。
UPDATE provider_channel
SET queued = (
  SELECT COUNT(*) FROM generation_task_handoff WHERE state IN ('PENDING','CLAIMED','RETRY')
)
WHERE code = 'simulated-primary';

-- 任务状态扩展为可解释的调度与待跟进阶段；账务仍只允许 FROZEN。
ALTER TABLE generation_task
  MODIFY COLUMN state VARCHAR(32) NOT NULL,
  ADD COLUMN state_note VARCHAR(255) NOT NULL DEFAULT '' AFTER accounting_state;

ALTER TABLE generation_task DROP CHECK chk_generation_task_state;

ALTER TABLE generation_task ADD CONSTRAINT chk_generation_task_state CHECK (state IN (
  'QUEUED','DISPATCHING','SUBMITTED','RUNNING','RECONCILING','RESULT_PENDING',
  'FAILED_PENDING_FOLLOWUP','CANCELLED_PENDING_FOLLOWUP','QUEUE_TIMEOUT','REVIEW'
));

-- 交接行记录认领人、租约、代次与重试时间；过期租约可被新调度者接管。
ALTER TABLE generation_task_handoff
  ADD COLUMN claim_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD COLUMN lease_until DATETIME(3) NULL,
  ADD COLUMN generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN attempts INT UNSIGNED NOT NULL DEFAULT 0,
  ADD COLUMN next_attempt_at DATETIME(3) NULL,
  ADD COLUMN enqueued_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN completed_at DATETIME(3) NULL,
  ADD COLUMN completion_reason VARCHAR(32) NULL,
  ADD COLUMN last_error VARCHAR(255) NULL,
  ADD KEY idx_generation_task_handoff_due (state, next_attempt_at, id),
  ADD KEY idx_generation_task_handoff_lease (state, lease_until, id);

ALTER TABLE generation_task_handoff DROP CHECK chk_generation_task_handoff_state;

ALTER TABLE generation_task_handoff ADD CONSTRAINT chk_generation_task_handoff_state
  CHECK (state IN ('PENDING','CLAIMED','RETRY','COMPLETED'));

-- 既有交接行按原任务身份纳入调度；只从接管时刻开始计算排队时限，不重建任务或冻结。
UPDATE generation_task_handoff
SET enqueued_at = CURRENT_TIMESTAMP(3), next_attempt_at = CURRENT_TIMESTAMP(3)
WHERE state = 'PENDING';
