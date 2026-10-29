-- 检查点只记录已完成的缓存失效批次，便于审计与延迟观测；
-- Canal 的确认位点仍以服务端 meta 为准，不把两者宣称为原子提交。
CREATE TABLE cache_cdc_checkpoint (
  destination VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  batch_id BIGINT NOT NULL,
  binlog_file VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  binlog_offset BIGINT NOT NULL,
  event_time_ms BIGINT NOT NULL,
  processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (destination),
  CONSTRAINT chk_cache_cdc_checkpoint_values CHECK (
    batch_id >= 0 AND binlog_offset >= 0 AND event_time_ms >= 0
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
