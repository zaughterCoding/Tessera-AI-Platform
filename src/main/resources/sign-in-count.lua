-- KEYS[1]: 用户当月位图；ARGV[1]: 今天对应的位（0 起）
-- 从今天向前数连续签到；今天未签到为 0，且不跨越月初。
local offset = tonumber(ARGV[1])
local count = 0
for bit = offset, 0, -1 do
  if redis.call('GETBIT', KEYS[1], bit) == 1 then
    count = count + 1
  else
    break
  end
end
return count
