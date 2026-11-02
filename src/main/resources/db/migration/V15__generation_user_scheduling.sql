-- 空值表示跟随全局默认值，调整全局配置时不必回填存量用户。
ALTER TABLE generation_user_quota
  ADD COLUMN active_limit INT UNSIGNED NULL AFTER active_tasks,
  ADD COLUMN schedule_weight INT UNSIGNED NULL AFTER active_limit,
  ADD CONSTRAINT chk_generation_user_quota_active_limit
    CHECK (active_limit IS NULL OR active_limit BETWEEN 1 AND 200),
  ADD CONSTRAINT chk_generation_user_quota_schedule_weight
    CHECK (schedule_weight IS NULL OR schedule_weight BETWEEN 1 AND 10);
