-- 限量活动是独立售卖身份：同一商品的不同活动分别持有库存、时间窗和恢复代次。
CREATE TABLE limited_campaign (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_id BIGINT UNSIGNED NOT NULL,
  initial_stock INT UNSIGNED NOT NULL,
  remaining_stock INT UNSIGNED NOT NULL,
  begin_at DATETIME(3) NOT NULL,
  end_at DATETIME(3) NOT NULL,
  publish_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  generation INT UNSIGNED NOT NULL DEFAULT 1,
  published_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_limited_campaign_product (product_id, id),
  KEY idx_limited_campaign_window (publish_state, begin_at, end_at),
  CONSTRAINT fk_limited_campaign_product FOREIGN KEY (product_id)
    REFERENCES usage_product (id),
  CONSTRAINT chk_limited_campaign_stock CHECK (
    initial_stock > 0 AND remaining_stock <= initial_stock
  ),
  CONSTRAINT chk_limited_campaign_window CHECK (begin_at < end_at),
  CONSTRAINT chk_limited_campaign_state CHECK (
    publish_state IN ('DRAFT','ACTIVE','PAUSED')
  ),
  CONSTRAINT chk_limited_campaign_generation CHECK (generation > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 每次重建生成新的库存代次；旧代次事件不得再改变新代次库存。
CREATE TABLE campaign_generation (
  campaign_id BIGINT UNSIGNED NOT NULL,
  generation INT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL,
  remaining_stock INT UNSIGNED NOT NULL,
  rebuilt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  replaced_at DATETIME(3) NULL,
  PRIMARY KEY (campaign_id, generation),
  KEY idx_campaign_generation_status (campaign_id, status),
  CONSTRAINT fk_campaign_generation_campaign FOREIGN KEY (campaign_id)
    REFERENCES limited_campaign (id),
  CONSTRAINT chk_campaign_generation_status CHECK (
    status IN ('DRAFT','ACTIVE','REPLACED')
  ),
  CONSTRAINT chk_campaign_generation_replaced CHECK (
    (status = 'REPLACED' AND replaced_at IS NOT NULL)
    OR (status <> 'REPLACED' AND replaced_at IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 预购资格与订单一一对应；每个用户在每个活动最多保留一个有效资格。
-- active_user 在失败或订单关闭释放后变为 NULL，从而保留历史并允许重新参与。
CREATE TABLE campaign_reservation (
  order_id BIGINT UNSIGNED NOT NULL,
  campaign_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  generation INT UNSIGNED NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  state VARCHAR(16) NOT NULL,
  stock_released TINYINT(1) NOT NULL DEFAULT 0,
  released_at DATETIME(3) NULL,
  attempts INT UNSIGNED NOT NULL DEFAULT 0,
  last_error VARCHAR(255) NULL,
  active_user BIGINT UNSIGNED GENERATED ALWAYS AS (
    CASE WHEN state = 'FAILED' OR stock_released = 1 THEN NULL ELSE user_id END
  ) STORED,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (order_id),
  UNIQUE KEY uk_campaign_reservation_active (campaign_id, active_user),
  KEY idx_campaign_reservation_request (campaign_id, user_id, request_key),
  KEY idx_campaign_reservation_state (campaign_id, state),
  CONSTRAINT fk_campaign_reservation_campaign FOREIGN KEY (campaign_id)
    REFERENCES limited_campaign (id),
  CONSTRAINT fk_campaign_reservation_user FOREIGN KEY (user_id)
    REFERENCES creator_user (id),
  CONSTRAINT chk_campaign_reservation_state CHECK (
    state IN ('PROCESSING','SUCCESS','REVIEW','FAILED')
  ),
  CONSTRAINT chk_campaign_reservation_release CHECK (
    (stock_released = 0 AND released_at IS NULL)
    OR (stock_released = 1 AND state = 'SUCCESS' AND released_at IS NOT NULL)
  ),
  CONSTRAINT chk_campaign_reservation_generation CHECK (generation > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 无法归属或无法自动恢复的预约消息保留供人工复核，不参与库存释放。
CREATE TABLE campaign_dead_letter (
  message_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  campaign_id BIGINT UNSIGNED NULL,
  order_id BIGINT UNSIGNED NULL,
  payload VARCHAR(2048) NOT NULL DEFAULT '',
  reason VARCHAR(255) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (message_id),
  KEY idx_campaign_dead_letter_campaign (campaign_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 活动订单不属于请求台账；普通购买仍保持 request_id 非空。
ALTER TABLE purchase_order
  MODIFY COLUMN request_id BIGINT UNSIGNED NULL,
  ADD COLUMN campaign_id BIGINT UNSIGNED NULL AFTER request_id,
  ADD COLUMN campaign_generation INT UNSIGNED NULL AFTER campaign_id,
  ADD KEY idx_purchase_order_campaign (campaign_id, id),
  ADD CONSTRAINT fk_purchase_order_campaign FOREIGN KEY (campaign_id)
    REFERENCES limited_campaign (id),
  ADD CONSTRAINT chk_purchase_order_campaign CHECK (
    (sale_mode = 0 AND campaign_id IS NULL AND campaign_generation IS NULL)
    OR (sale_mode = 1 AND campaign_id IS NOT NULL AND campaign_generation IS NOT NULL)
  );

-- 履约交接任务按订单维度幂等，重复消息不会生成第二条建单任务。
ALTER TABLE business_outbox
  ADD COLUMN idempotency_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD UNIQUE KEY uk_business_outbox_idempotency (idempotency_key);
