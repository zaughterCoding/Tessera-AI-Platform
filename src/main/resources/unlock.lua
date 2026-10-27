-- 只允许锁的持有者释放自己的锁，避免锁过期后误删他人的锁。
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
end
return 0
