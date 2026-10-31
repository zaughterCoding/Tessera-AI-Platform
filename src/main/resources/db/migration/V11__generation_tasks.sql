-- 同一用户同一请求键只对应一个任务请求；摘要不同说明业务请求已变化，不能复用原任务。
CREATE TABLE generation_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  task_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_generation_request_user_key (user_id, request_key),
  UNIQUE KEY uk_generation_request_task (task_id),
  CONSTRAINT fk_generation_request_user FOREIGN KEY (user_id) REFERENCES creator_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务保存提交时的请求快照与权益包快照；队列状态与账务状态分开表达。
CREATE TABLE generation_task (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  request_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  application_name VARCHAR(128) NOT NULL,
  application_version INT UNSIGNED NOT NULL,
  model_version VARCHAR(64) NOT NULL DEFAULT '',
  image_size VARCHAR(32) NOT NULL DEFAULT '',
  output_count INT UNSIGNED NOT NULL,
  prompt TEXT NOT NULL,
  negative_prompt VARCHAR(1000) NOT NULL DEFAULT '',
  grant_id BIGINT UNSIGNED NOT NULL,
  frozen_units INT UNSIGNED NOT NULL,
  state VARCHAR(16) NOT NULL,
  accounting_state VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_generation_task_user_key (user_id, request_key),
  UNIQUE KEY uk_generation_task_request (request_id),
  KEY idx_generation_task_user (user_id, id),
  KEY idx_generation_task_grant (grant_id, id),
  KEY idx_generation_task_state (state, id),
  CONSTRAINT fk_generation_task_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT fk_generation_task_request FOREIGN KEY (request_id)
    REFERENCES generation_request (id),
  CONSTRAINT fk_generation_task_application FOREIGN KEY (application_id)
    REFERENCES ai_application (id),
  CONSTRAINT fk_generation_task_grant FOREIGN KEY (grant_id) REFERENCES entitlement_grant (id),
  CONSTRAINT chk_generation_task_state CHECK (state IN ('QUEUED')),
  CONSTRAINT chk_generation_task_accounting CHECK (accounting_state IN ('FROZEN')),
  CONSTRAINT chk_generation_task_units CHECK (frozen_units > 0),
  CONSTRAINT chk_generation_task_output CHECK (output_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务素材引用与作品引用同为删除保护事实；输入素材在接受后不能被立即删除。
CREATE TABLE generation_task_media (
  task_id BIGINT UNSIGNED NOT NULL,
  media_id BIGINT UNSIGNED NOT NULL,
  position TINYINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (task_id, media_id),
  UNIQUE KEY uk_generation_task_media_position (task_id, position),
  KEY idx_generation_task_media_media (media_id, task_id),
  CONSTRAINT fk_generation_task_media_task FOREIGN KEY (task_id)
    REFERENCES generation_task (id) ON DELETE CASCADE,
  CONSTRAINT fk_generation_task_media_asset FOREIGN KEY (media_id) REFERENCES media_asset (id),
  CONSTRAINT chk_generation_task_media_position CHECK (position < 4)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 交接责任先持久登记，N03 按任务身份接管；本阶段没有消费者，也不提前标记完成。
CREATE TABLE generation_task_handoff (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id BIGINT UNSIGNED NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_generation_task_handoff_task (task_id),
  KEY idx_generation_task_handoff_state (state, id),
  CONSTRAINT fk_generation_task_handoff_task FOREIGN KEY (task_id)
    REFERENCES generation_task (id),
  CONSTRAINT chk_generation_task_handoff_state CHECK (state IN ('PENDING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 本人按有效期选包需要稳定顺序；索引不改变任何历史余额或流水。
ALTER TABLE entitlement_grant
  ADD KEY idx_entitlement_grant_submission (user_id, status, expires_at, id);

-- 冻结是新的账务事实类型；保留历史流水与既有余额恒等式，引用列只追加不覆写。
ALTER TABLE entitlement_ledger
  DROP CHECK chk_entitlement_ledger_type;

ALTER TABLE entitlement_ledger
  ADD CONSTRAINT chk_entitlement_ledger_type
    CHECK (operation_type IN ('GRANT','EXPIRE','CORRECTION','FREEZE')),
  ADD COLUMN reference_type VARCHAR(24) NOT NULL DEFAULT '' AFTER reason,
  ADD COLUMN reference_id BIGINT UNSIGNED NULL AFTER reference_type,
  ADD KEY idx_entitlement_ledger_reference (reference_type, reference_id);
