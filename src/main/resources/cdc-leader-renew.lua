-- 仅锁持有者可以续租；进程暂停超过租约后不能再确认批次。
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('pexpire', KEYS[1], ARGV[2])
end
return 0
