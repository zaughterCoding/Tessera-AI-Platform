-- 自适应并发的持久状态：重启后继续使用上次的生效值；max_in_flight 仍是硬上限。
ALTER TABLE provider_channel
  ADD COLUMN effective_in_flight INT UNSIGNED NULL AFTER in_flight,
  ADD COLUMN dispatch_paused_until DATETIME(3) NULL AFTER effective_in_flight,
  ADD COLUMN adaptive_window_at DATETIME(3) NULL AFTER dispatch_paused_until,
  ADD CONSTRAINT chk_provider_channel_effective_in_flight
    CHECK (effective_in_flight IS NULL OR effective_in_flight > 0);
