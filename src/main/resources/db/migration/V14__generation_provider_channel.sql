-- 真实渠道与模拟渠道使用同一调度、尝试、回调与结算结构：渠道行只表达能力与容量差异。
-- 真实渠道默认停用，未配置地址、凭据与额度时不会产生任何外部调用。
ALTER TABLE provider_channel DROP CHECK chk_provider_channel_kind;

ALTER TABLE provider_channel ADD CONSTRAINT chk_provider_channel_kind
  CHECK (kind IN ('SIMULATED','HTTP'));

INSERT INTO provider_channel(
  code, display_name, kind, status, max_in_flight, in_flight, queue_capacity, queued,
  queue_timeout_seconds, supports_idempotent_submit, supports_query_by_attempt, supports_cancel
) VALUES
  ('http-image', 'HTTP 图片渠道', 'HTTP', 'DISABLED', 2, 0, 20, 0, 1800, 0, 0, 0);

-- 本地真实调用额度必须跨进程与重启持久：条件更新失败时不放行，也不会把失败记成已用额度。
CREATE TABLE provider_call_budget (
  channel_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  calls_used INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (channel_code),
  CONSTRAINT fk_provider_call_budget_channel FOREIGN KEY (channel_code)
    REFERENCES provider_channel (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
