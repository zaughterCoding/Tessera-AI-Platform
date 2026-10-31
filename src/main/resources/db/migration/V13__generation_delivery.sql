-- 结果交付与最终结算：任务从“冻结中”收敛到“已消费”或“已释放”。
-- 历史任务行、流水与冻结事实全部保留；本迁移只扩展状态集合和追加新事实表。

ALTER TABLE entitlement_ledger DROP CHECK chk_entitlement_ledger_type;

ALTER TABLE entitlement_ledger
  ADD CONSTRAINT chk_entitlement_ledger_type
    CHECK (operation_type IN ('GRANT','EXPIRE','CORRECTION','FREEZE','CONSUME','RELEASE'));

ALTER TABLE generation_task
  MODIFY COLUMN state VARCHAR(32) NOT NULL,
  MODIFY COLUMN accounting_state VARCHAR(16) NOT NULL,
  ADD COLUMN delivered_at DATETIME(3) NULL AFTER state_note,
  ADD COLUMN settled_at DATETIME(3) NULL AFTER delivered_at;

ALTER TABLE generation_task DROP CHECK chk_generation_task_state;

ALTER TABLE generation_task ADD CONSTRAINT chk_generation_task_state CHECK (state IN (
  'QUEUED','DISPATCHING','SUBMITTED','RUNNING','RECONCILING','RESULT_PENDING',
  'FAILED_PENDING_FOLLOWUP','CANCELLED_PENDING_FOLLOWUP','QUEUE_TIMEOUT','REVIEW',
  'CANCEL_REQUESTED','SUCCEEDED','FAILED','CANCELLED'
));

ALTER TABLE generation_task DROP CHECK chk_generation_task_accounting;

ALTER TABLE generation_task ADD CONSTRAINT chk_generation_task_accounting CHECK (
  accounting_state IN ('FROZEN','CONSUMED','RELEASED')
);

ALTER TABLE generation_task ADD KEY idx_generation_task_accounting (accounting_state, id);

-- 一条上游结果对应一个稳定位置；文件引用只在服务端验证并登记后写入。
CREATE TABLE generation_result (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  attempt_id BIGINT UNSIGNED NOT NULL,
  position SMALLINT UNSIGNED NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  source_ref VARCHAR(512) NOT NULL,
  expected_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  media_id BIGINT UNSIGNED NULL,
  content_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  size_bytes BIGINT UNSIGNED NULL,
  content_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  save_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  lease_until DATETIME(3) NULL,
  generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NULL,
  last_error VARCHAR(255) NULL,
  saved_at DATETIME(3) NULL,
  delivered_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_generation_result_task_position (task_id, position),
  UNIQUE KEY uk_generation_result_media (media_id),
  KEY idx_generation_result_due (state, next_attempt_at, id),
  KEY idx_generation_result_lease (state, lease_until, id),
  CONSTRAINT fk_generation_result_task FOREIGN KEY (task_id)
    REFERENCES generation_task (id),
  CONSTRAINT fk_generation_result_attempt FOREIGN KEY (attempt_id)
    REFERENCES provider_attempt (id),
  CONSTRAINT fk_generation_result_media FOREIGN KEY (media_id) REFERENCES media_asset (id),
  CONSTRAINT chk_generation_result_state CHECK (
    state IN ('PENDING','SAVING','SAVED','DELIVERED','FAILED')
  ),
  CONSTRAINT chk_generation_result_position CHECK (position < 100),
  CONSTRAINT chk_generation_result_digest CHECK (CHAR_LENGTH(expected_digest) = 64),
  CONSTRAINT chk_generation_result_saved CHECK (
    state NOT IN ('SAVED','DELIVERED')
    OR (media_id IS NOT NULL AND content_digest IS NOT NULL AND size_bytes > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 每个任务恰好一条最终结算；任务行条件更新是第一道闸门，本表唯一键是第二道。
CREATE TABLE generation_settlement (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  grant_id BIGINT UNSIGNED NOT NULL,
  kind VARCHAR(16) NOT NULL,
  release_target VARCHAR(16) NULL,
  units INT UNSIGNED NOT NULL,
  ledger_id BIGINT UNSIGNED NULL,
  reason VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_generation_settlement_task (task_id),
  UNIQUE KEY uk_generation_settlement_ledger (ledger_id),
  KEY idx_generation_settlement_grant (grant_id, id),
  CONSTRAINT fk_generation_settlement_task FOREIGN KEY (task_id)
    REFERENCES generation_task (id),
  CONSTRAINT fk_generation_settlement_grant FOREIGN KEY (grant_id)
    REFERENCES entitlement_grant (id),
  CONSTRAINT fk_generation_settlement_ledger FOREIGN KEY (ledger_id)
    REFERENCES entitlement_ledger (id),
  CONSTRAINT chk_generation_settlement_kind CHECK (kind IN ('CONSUME','RELEASE')),
  CONSTRAINT chk_generation_settlement_target CHECK (
    (kind = 'CONSUME' AND release_target IS NULL)
    OR (kind = 'RELEASE' AND release_target IN ('AVAILABLE','EXPIRED'))
  ),
  CONSTRAINT chk_generation_settlement_units CHECK (units > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 取消意图先持久登记；无尝试的排队取消由后台在确认未提交后幂等完成。
CREATE TABLE generation_cancel_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  outcome VARCHAR(48) NULL,
  reason VARCHAR(200) NOT NULL DEFAULT '',
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  lease_until DATETIME(3) NULL,
  generation BIGINT UNSIGNED NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NULL,
  last_error VARCHAR(255) NULL,
  requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  resolved_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_generation_cancel_task (task_id),
  KEY idx_generation_cancel_due (state, next_attempt_at, id),
  KEY idx_generation_cancel_lease (state, lease_until, id),
  KEY idx_generation_cancel_user (user_id, id),
  CONSTRAINT fk_generation_cancel_task FOREIGN KEY (task_id) REFERENCES generation_task (id),
  CONSTRAINT fk_generation_cancel_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT chk_generation_cancel_state CHECK (
    state IN ('PENDING','RETRYING','RESOLVED','REJECTED','UNSUPPORTED')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 媒体来源区分用户上传与系统生成；生成结果同样受配额、引用保护和孤儿清理约束。
ALTER TABLE media_asset
  ADD COLUMN source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    DEFAULT 'UPLOAD' AFTER state,
  ADD CONSTRAINT chk_media_asset_source CHECK (source IN ('UPLOAD','GENERATED'));

-- 模拟上游记录提交时的输出数量，用以为每个位置生成可验证的独立文件。
ALTER TABLE simulated_provider_job
  ADD COLUMN output_count INT UNSIGNED NOT NULL DEFAULT 1 AFTER duration_ms,
  ADD CONSTRAINT chk_simulated_provider_output CHECK (output_count > 0);
