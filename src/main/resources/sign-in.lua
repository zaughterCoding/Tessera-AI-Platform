-- KEYS[1]: 用户当月位图；ARGV[1]: 日期位（0 起）；ARGV[2]: 保留秒数
-- 返回 1 表示今天此前已签到；重复调用不会重置已有点位。
local prior = redis.call('GETBIT', KEYS[1], ARGV[1])
redis.call('SETBIT', KEYS[1], ARGV[1], 1)
redis.call('EXPIRE', KEYS[1], ARGV[2])
return prior
