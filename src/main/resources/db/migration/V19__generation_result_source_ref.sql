-- 对象存储的签名下载地址可超过 1500 字符；结果清单需要保存完整地址才能下载。
ALTER TABLE generation_result MODIFY COLUMN source_ref VARCHAR(2048) NOT NULL;
