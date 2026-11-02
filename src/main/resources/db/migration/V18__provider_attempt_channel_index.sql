-- 外键依托的索引含 state 时，每次改状态都会重写索引并对渠道行加共享锁；
-- 同一事务随后更新渠道计数要升级排他锁，并发时形成死锁。外键只需 channel_id。
ALTER TABLE provider_attempt
  ADD KEY idx_provider_attempt_channel_fk (channel_id),
  DROP KEY idx_provider_attempt_channel;
