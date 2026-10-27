-- 身份和过期时间一起保存，避免写入后中断留下永久会话。
if redis.call('EXISTS',KEYS[1])==1 then return 0 end
redis.call('HSET',KEYS[1],'id',ARGV[1])
redis.call('EXPIRE',KEYS[1],ARGV[2])
return 1
