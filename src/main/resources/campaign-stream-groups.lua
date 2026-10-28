-- 只读列出全部消费组的 Pending、lag 与最后投递 ID；lag 无法确定时返回 -1。
-- KEYS: Stream。返回扁平数组：组名, Pending, lag, 最后投递 ID, ...
if redis.call('TYPE', KEYS[1]).ok ~= 'stream' then return {} end
local groups = redis.call('XINFO', 'GROUPS', KEYS[1])
local result = {}
for _, group in ipairs(groups) do
  local name = ''
  local pending = 0
  local lag = -1
  local last = ''
  for i = 1, #group, 2 do
    local field = group[i]
    local value = group[i + 1]
    if field == 'name' then
      name = value
    elseif field == 'pending' then
      pending = tonumber(value) or 0
    elseif field == 'lag' then
      if type(value) == 'number' then lag = value else lag = -1 end
    elseif field == 'last-delivered-id' then
      if type(value) == 'string' then last = value end
    end
  end
  table.insert(result, name)
  table.insert(result, pending)
  table.insert(result, lag)
  table.insert(result, last)
end
return result
