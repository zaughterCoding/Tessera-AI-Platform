-- 只有仍持有锁且版本未推进的加载者才能回填，防止锁过期和旧值覆盖新值。
if redis.call('GET', KEYS[2]) ~= ARGV[4] then
  return 0
end
local version = redis.call('GET', KEYS[3]) or '0'
if version ~= ARGV[3] then
  return 0
end
redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
return 1
