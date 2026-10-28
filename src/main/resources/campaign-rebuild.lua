-- 重建活动库存、代次与有效资格；先清空参与者索引，再按数据库事实写入。
-- KEYS: 库存, 参与者集合, 用户->订单哈希, 活动哈希, 带入代次哈希。
-- ARGV: 活动 ID, 代次, 库存, 开始毫秒, 结束毫秒, 状态, 之后为用户/订单对。
-- 返回：写入的有效资格数量。
local types = {'string', 'set', 'hash', 'hash', 'hash'}
for i = 1, 5 do
  local kind = redis.call('TYPE', KEYS[i]).ok
  if kind ~= 'none' and kind ~= types[i] then return -1 end
end
redis.call('DEL', KEYS[2])
redis.call('DEL', KEYS[3])
redis.call('DEL', KEYS[5])
redis.call('SET', KEYS[1], ARGV[3])
redis.call('HSET', KEYS[4],
  'campaignId', ARGV[1], 'generation', ARGV[2],
  'begin', ARGV[4], 'end', ARGV[5], 'state', ARGV[6])
local written = 0
for i = 7, #ARGV, 2 do
  redis.call('SADD', KEYS[2], ARGV[i])
  redis.call('HSET', KEYS[3], ARGV[i], ARGV[i + 1])
  redis.call('HSET', KEYS[5], ARGV[i + 1], ARGV[2])
  written = written + 1
end
return written
