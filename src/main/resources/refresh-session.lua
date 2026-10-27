-- 读取和续期原子执行；不存在的会话不能被续期操作复活。
local id=redis.call('HGET',KEYS[1],'id')
if id then redis.call('EXPIRE',KEYS[1],ARGV[1]) end
return id
