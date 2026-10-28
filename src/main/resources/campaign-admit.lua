-- 入口判定、预占库存、登记资格与写入 Stream 在同一脚本内原子完成。
-- KEYS: 库存, 参与者集合, Stream, 活动哈希, 用户->订单哈希, 订单哈希, 请求键映射。
-- ARGV: 订单号, 用户 ID, 活动 ID, 请求键, 订单哈希有效期秒数。
-- 返回：0 已受理；1 售罄；2 已有资格；3 不在可参与状态；4 类型冲突；5 代次不完整；6 重复请求。
local types = {'string', 'set', 'stream', 'hash', 'hash', 'hash', 'string'}
for i = 1, 7 do
  local kind = redis.call('TYPE', KEYS[i]).ok
  if kind ~= 'none' and kind ~= types[i] then return 4 end
end
local replay = redis.call('GET', KEYS[7])
if replay then return 6 end
local stock = tonumber(redis.call('GET', KEYS[1]))
local beginAt = tonumber(redis.call('HGET', KEYS[4], 'begin'))
local endAt = tonumber(redis.call('HGET', KEYS[4], 'end'))
local state = redis.call('HGET', KEYS[4], 'state')
local generation = tonumber(redis.call('HGET', KEYS[4], 'generation'))
if not stock or not beginAt or not endAt or not generation or state ~= 'ACTIVE' then
  return 3
end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if now < beginAt or now > endAt then return 3 end
-- 重复参与优先返回已有资格，即使库存已经售罄。
if redis.call('SISMEMBER', KEYS[2], ARGV[2]) == 1 then return 2 end
if stock <= 0 then return 1 end
redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[2])
redis.call('HSET', KEYS[5], ARGV[2], ARGV[1])
redis.call('HSET', KEYS[6],
  'orderId', ARGV[1], 'userId', ARGV[2], 'campaignId', ARGV[3],
  'state', 'PROCESSING', 'generation', generation, 'requestKey', ARGV[4])
redis.call('EXPIRE', KEYS[6], tonumber(ARGV[5]))
redis.call('SET', KEYS[7], ARGV[1], 'EX', tonumber(ARGV[5]))
redis.call('XADD', KEYS[3], '*',
  'orderId', ARGV[1], 'userId', ARGV[2], 'campaignId', ARGV[3],
  'generation', generation, 'requestKey', ARGV[4])
return 0
