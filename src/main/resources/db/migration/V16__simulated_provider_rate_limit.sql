-- 模拟上游增加限流故障；只影响显式开启故障控制的演示环境。
ALTER TABLE simulated_provider_fault DROP CHECK chk_simulated_provider_fault_mode;

ALTER TABLE simulated_provider_fault ADD CONSTRAINT chk_simulated_provider_fault_mode CHECK (mode IN (
  'REJECT_SUBMIT','LOSE_SUBMIT_RESPONSE','TIMEOUT_BUT_RUNNING','SLOW','FAIL',
  'QUERY_TEMPORARY_FAILURE','RATE_LIMITED'
));
