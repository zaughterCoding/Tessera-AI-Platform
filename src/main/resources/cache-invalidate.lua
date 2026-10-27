-- 推进该键版本并删除数据，再通知各实例只失效对应键。
redis.call('INCR', KEYS[2])
redis.call('DEL', KEYS[1])
redis.call('PUBLISH', ARGV[1], KEYS[1])
return 1
