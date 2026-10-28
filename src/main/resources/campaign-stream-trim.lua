-- 在 Redis 单线程内重新核对全部消费组并裁剪，避免快照与执行之间出现新组或新水位。
-- 任一消费组存在 Pending、lag 未知、lag 大于 0 或没有可用水位时不裁剪。
-- KEYS: Stream；ARGV[1]: 必须存在的默认消费组。返回 0 未执行；大于 0 表示已执行，
-- 值为删除条数 + 1。
if redis.call('TYPE', KEYS[1]).ok ~= 'stream' then return 0 end
local groups = redis.call('XINFO', 'GROUPS', KEYS[1])
if #groups == 0 then return 0 end
local defaultGroup = ARGV[1]
local foundDefault = false
local minMillis = nil
local minSequence = nil
for _, group in ipairs(groups) do
  local name = nil
  local pending = nil
  local lag = nil
  local last = nil
  for i = 1, #group, 2 do
    local field = group[i]
    local value = group[i + 1]
    if field == 'name' then
      if type(value) == 'string' then name = value end
    elseif field == 'pending' then
      pending = tonumber(value)
    elseif field == 'lag' then
      if type(value) == 'number' then lag = value end
    elseif field == 'last-delivered-id' then
      if type(value) == 'string' then last = value end
    end
  end
  if name == defaultGroup then foundDefault = true end
  if pending == nil or pending > 0 then return 0 end
  if lag == nil or lag > 0 then return 0 end
  if last == nil then return 0 end
  local millisText, sequenceText = string.match(last, '^(%d+)-(%d+)$')
  if not millisText then return 0 end
  local millis = tonumber(millisText)
  local sequence = tonumber(sequenceText)
  if millis == 0 and sequence == 0 then return 0 end
  if minMillis == nil or millis < minMillis
      or (millis == minMillis and sequence < minSequence) then
    minMillis = millis
    minSequence = sequence
  end
end
-- 默认组被删除或尚未建立时没有可信的恢复边界，不能凭其他组的水位裁剪。
if not foundDefault or minMillis == nil then return 0 end
-- 推进一个序号，既删掉最小已确认水位，又保留其他消费组尚未确认之前的消息。
local nextId
if minSequence >= 9007199254740991 then
  nextId = (minMillis + 1) .. '-0'
else
  nextId = minMillis .. '-' .. (minSequence + 1)
end
local removed = redis.call('XTRIM', KEYS[1], 'MINID', nextId)
return tonumber(removed) + 1
