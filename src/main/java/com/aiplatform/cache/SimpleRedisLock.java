package com.aiplatform.cache;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 带持有者标识的 Redis 锁，释放时只删除自己的锁。 */
public class SimpleRedisLock implements ILock {
  private static final String ID_PREFIX = UUID.randomUUID().toString().replace("-", "") + "-";
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
      CacheScripts.number("unlock.lua");
  private final String name;
  private final StringRedisTemplate redis;
  private final String namespace;

  public SimpleRedisLock(String name, StringRedisTemplate redis, String namespace) {
    this.name = name;
    this.redis = redis;
    this.namespace = namespace;
  }

  private String key() {
    return namespace + ":lock:" + name;
  }

  private String owner() {
    return ID_PREFIX + Thread.currentThread().getId();
  }

  @Override
  public boolean tryLock(long timeoutSec) {
    Boolean success = redis.opsForValue().setIfAbsent(
        key(), owner(), timeoutSec, TimeUnit.SECONDS);
    return Boolean.TRUE.equals(success);
  }

  @Override
  public void unlock() {
    // 以持有者标识校验后再删除，锁过期后的旧线程不能误删新持有者的锁。
    redis.execute(UNLOCK_SCRIPT, Collections.singletonList(key()), owner());
  }
}
