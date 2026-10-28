-- 只释放仍属于该订单的资格；旧代次、旧订单和重复事件都不能重复增加库存。
-- KEYS: 库存, 参与者集合, 用户->订单哈希, 订单哈希, 活动哈希, 带入代次哈希。
-- ARGV: 用户 ID, 订单号, 预约代次, 目标状态, 请求键映射 Key, 活动初始库存。
-- 返回：1 已释放；0 安全空操作；-1 状态缺失；-2 类型冲突；-3 会导致库存超发。
local types = {'string', 'set', 'hash', 'hash', 'hash', 'hash'}
for i = 1, 6 do
  local kind = redis.call('TYPE', KEYS[i]).ok
  if kind ~= 'none' and kind ~= types[i] then return -2 end
end
local stock = redis.call('GET', KEYS[1])
local initialStock = tonumber(ARGV[6])
if not stock or not tonumber(stock) or not initialStock then return -1 end
local generation = tonumber(redis.call('HGET', KEYS[5], 'generation'))
if not generation then return -1 end
local reservationGeneration = tonumber(ARGV[3])
local carried = redis.call('HGET', KEYS[6], ARGV[2])
if generation ~= reservationGeneration
    and tonumber(carried) ~= generation then return 0 end
local owner = redis.call('HGET', KEYS[3], ARGV[1])
if owner ~= ARGV[2] then return 0 end
if tonumber(stock) >= initialStock then return -3 end
redis.call('SREM', KEYS[2], ARGV[1])
redis.call('HDEL', KEYS[3], ARGV[1])
redis.call('HDEL', KEYS[6], ARGV[2])
redis.call('INCR', KEYS[1])
redis.call('HSET', KEYS[4], 'state', ARGV[4])
if ARGV[5] ~= '' then
  local mapped = redis.call('GET', ARGV[5])
  if mapped == ARGV[2] then redis.call('DEL', ARGV[5]) end
end
return 1
