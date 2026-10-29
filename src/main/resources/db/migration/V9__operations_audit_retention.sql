-- 运营操作审计只记录操作者、对象身份与业务结果，不保存请求载荷、凭据或用户文件内容。
CREATE TABLE operation_audit (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  operator_id BIGINT UNSIGNED NULL,
  operator_roles VARCHAR(128) NOT NULL DEFAULT '',
  action VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  object_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  object_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  error_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  detail VARCHAR(512) NULL,
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_operation_audit_time (occurred_at, id),
  KEY idx_operation_audit_operator (operator_id, occurred_at, id),
  KEY idx_operation_audit_action (action, occurred_at, id),
  KEY idx_operation_audit_object (object_type, object_id, occurred_at),
  CONSTRAINT chk_operation_audit_outcome CHECK (outcome IN ('SUCCESS','FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 保留清理按完成时间和主键顺序推进；索引只服务已终结状态的截断，不影响在途任务。
ALTER TABLE business_outbox
  ADD KEY idx_business_outbox_retention (state, consumed_at, id);

ALTER TABLE message_inbox
  ADD KEY idx_message_inbox_retention (processed_at, consumer_group, event_id);

ALTER TABLE message_failure
  ADD KEY idx_message_failure_retention (created_at, consumer_group, event_key);

ALTER TABLE analytics_failure
  ADD KEY idx_analytics_failure_retention (created_at);
