-- 权益包只以来源订单与权益项为身份；购买完成时间和使用规格全部来自订单快照。
CREATE TABLE entitlement_grant (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  item_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  application_name VARCHAR(128) NOT NULL,
  application_version INT UNSIGNED NOT NULL,
  model_version VARCHAR(64) NOT NULL DEFAULT '',
  image_size VARCHAR(32) NOT NULL DEFAULT '',
  output_count INT UNSIGNED NOT NULL,
  credits INT UNSIGNED NOT NULL,
  valid_days INT UNSIGNED NOT NULL,
  source_funding VARCHAR(16) NOT NULL,
  source_paid_at DATETIME(3) NOT NULL,
  total_credits INT UNSIGNED NOT NULL,
  available_credits INT UNSIGNED NOT NULL,
  frozen_credits INT UNSIGNED NOT NULL DEFAULT 0,
  consumed_credits INT UNSIGNED NOT NULL DEFAULT 0,
  expired_credits INT UNSIGNED NOT NULL DEFAULT 0,
  effective_from DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_entitlement_grant_source (order_id, item_key),
  KEY idx_entitlement_grant_user (user_id, id),
  KEY idx_entitlement_grant_expiry (status, expires_at, id),
  CONSTRAINT fk_entitlement_grant_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT fk_entitlement_grant_order FOREIGN KEY (order_id) REFERENCES purchase_order (id),
  CONSTRAINT chk_entitlement_grant_funding CHECK (source_funding IN ('FREE','MOCK')),
  CONSTRAINT chk_entitlement_grant_status CHECK (status IN ('ACTIVE','EXPIRED')),
  CONSTRAINT chk_entitlement_grant_package CHECK (
    credits > 0 AND valid_days > 0 AND output_count > 0
  ),
  CONSTRAINT chk_entitlement_grant_window CHECK (effective_from < expires_at),
  CONSTRAINT chk_entitlement_grant_balance CHECK (
    total_credits = available_credits + frozen_credits + consumed_credits + expired_credits
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 追加式账务流水；每次变化记录各分量增量及其后余额，余额可由发放流水重建。
CREATE TABLE entitlement_ledger (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  grant_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  item_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_key VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_type VARCHAR(24) NOT NULL,
  delta_total INT NOT NULL,
  delta_available INT NOT NULL,
  delta_frozen INT NOT NULL,
  delta_consumed INT NOT NULL,
  delta_expired INT NOT NULL,
  total_after INT UNSIGNED NOT NULL,
  available_after INT UNSIGNED NOT NULL,
  frozen_after INT UNSIGNED NOT NULL,
  consumed_after INT UNSIGNED NOT NULL,
  expired_after INT UNSIGNED NOT NULL,
  reason VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_entitlement_ledger_operation (operation_key),
  KEY idx_entitlement_ledger_grant (grant_id, id),
  KEY idx_entitlement_ledger_user (user_id, id),
  CONSTRAINT fk_entitlement_ledger_grant FOREIGN KEY (grant_id) REFERENCES entitlement_grant (id),
  CONSTRAINT fk_entitlement_ledger_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT fk_entitlement_ledger_order FOREIGN KEY (order_id) REFERENCES purchase_order (id),
  CONSTRAINT chk_entitlement_ledger_type
    CHECK (operation_type IN ('GRANT','EXPIRE','CORRECTION')),
  CONSTRAINT chk_entitlement_ledger_delta CHECK (
    delta_total = delta_available + delta_frozen + delta_consumed + delta_expired
  ),
  CONSTRAINT chk_entitlement_ledger_balance CHECK (
    total_after = available_after + frozen_after + consumed_after + expired_after
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 历史订单无法证明购买完成时间或支付事实时保留异常，不猜测时间、不静默发放。
CREATE TABLE entitlement_anomaly (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  item_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  anomaly_type VARCHAR(32) NOT NULL,
  detail VARCHAR(500) NOT NULL DEFAULT '',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  resolved_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_entitlement_anomaly_source (order_id, item_key, anomaly_type),
  KEY idx_entitlement_anomaly_status (status, id),
  CONSTRAINT fk_entitlement_anomaly_order FOREIGN KEY (order_id) REFERENCES purchase_order (id),
  CONSTRAINT fk_entitlement_anomaly_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT chk_entitlement_anomaly_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
