-- 同一用户同一请求键只允许一个订单；不同键是新的购买请求，不是换单入口。
CREATE TABLE purchase_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_purchase_request_user_key (user_id, request_key),
  UNIQUE KEY uk_purchase_request_order (order_id),
  CONSTRAINT fk_purchase_request_user FOREIGN KEY (user_id) REFERENCES creator_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 订单保存购买当时的应用、商品、规格与金额快照；后续商品改动不得回写历史。
CREATE TABLE purchase_order (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  request_id BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  application_id BIGINT UNSIGNED NOT NULL,
  application_name VARCHAR(128) NOT NULL,
  application_version INT UNSIGNED NOT NULL,
  product_title VARCHAR(128) NOT NULL,
  product_subtitle VARCHAR(255) NOT NULL DEFAULT '',
  product_rules VARCHAR(1024) NOT NULL DEFAULT '',
  model_version VARCHAR(64) NOT NULL DEFAULT '',
  image_size VARCHAR(32) NOT NULL DEFAULT '',
  output_count INT UNSIGNED NOT NULL,
  credits INT UNSIGNED NOT NULL,
  valid_days INT UNSIGNED NOT NULL,
  sale_mode TINYINT UNSIGNED NOT NULL,
  currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'CNY',
  unit_price BIGINT UNSIGNED NOT NULL,
  amount BIGINT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL,
  funding VARCHAR(16) NULL,
  expire_at DATETIME(3) NULL,
  paid_at DATETIME(3) NULL,
  closed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_purchase_order_request (request_id),
  KEY idx_purchase_order_user (user_id, id),
  KEY idx_purchase_order_expiry (status, expire_at, id),
  KEY idx_purchase_order_product (product_id, id),
  CONSTRAINT fk_purchase_order_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT fk_purchase_order_request FOREIGN KEY (request_id)
    REFERENCES purchase_request (id),
  CONSTRAINT chk_purchase_order_status
    CHECK (status IN ('UNPAID','PAID','CANCELLED')),
  CONSTRAINT chk_purchase_order_funding
    CHECK (funding IS NULL OR funding IN ('FREE','MOCK')),
  CONSTRAINT chk_purchase_order_amount CHECK (unit_price >= 0 AND amount >= 0),
  CONSTRAINT chk_purchase_order_package
    CHECK (credits > 0 AND valid_days > 0 AND output_count > 0),
  CONSTRAINT chk_purchase_order_state CHECK (
    (status = 'UNPAID' AND funding IS NULL AND paid_at IS NULL AND expire_at IS NOT NULL)
    OR (status = 'PAID' AND funding IS NOT NULL AND paid_at IS NOT NULL)
    OR (status = 'CANCELLED' AND funding IS NULL AND paid_at IS NULL
        AND closed_at IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 一单一笔支付事实；免费领取也记录完成事实，但明确不是真实支付。
CREATE TABLE purchase_payment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  channel VARCHAR(16) NOT NULL,
  amount BIGINT UNSIGNED NOT NULL,
  simulated TINYINT(1) NOT NULL,
  paid_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_purchase_payment_order (order_id),
  KEY idx_purchase_payment_user (user_id, id),
  CONSTRAINT fk_purchase_payment_order FOREIGN KEY (order_id) REFERENCES purchase_order (id),
  CONSTRAINT fk_purchase_payment_user FOREIGN KEY (user_id) REFERENCES creator_user (id),
  CONSTRAINT chk_purchase_payment_channel CHECK (channel IN ('FREE','MOCK')),
  CONSTRAINT chk_purchase_payment_simulated CHECK (
    (channel = 'MOCK' AND simulated = 1 AND amount >= 0)
    OR (channel = 'FREE' AND simulated = 0 AND amount = 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 事件投影只记录已发生的订单事实，不保存权益余额，也不作为发货判断依据。
CREATE TABLE purchase_order_event (
  event_id BIGINT UNSIGNED NOT NULL,
  order_id BIGINT UNSIGNED NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (event_id),
  KEY idx_purchase_order_event_order (order_id, event_id),
  CONSTRAINT fk_purchase_order_event_order FOREIGN KEY (order_id)
    REFERENCES purchase_order (id),
  CONSTRAINT chk_purchase_order_event_type
    CHECK (event_type IN ('ORDER_CREATED','ORDER_PAID','ORDER_CLAIMED','ORDER_CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
