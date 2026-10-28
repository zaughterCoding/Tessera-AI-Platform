-- Redis 7 把未投递条目与已进入 PEL 的条目分开报告。
local groups = redis.call('XINFO', 'GROUPS', KEYS[1])
for _, group in ipairs(groups) do
  local name, lag
  for i = 1, #group, 2 do
    if group[i] == 'name' then name = group[i + 1] end
    if group[i] == 'lag' then lag = group[i + 1] end
  end
  if name == ARGV[1] then return tonumber(lag) or -1 end
end
return -1
