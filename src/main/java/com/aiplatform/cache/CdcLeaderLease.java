package com.aiplatform.cache;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 单实例消费的 Redis 租约；续租与释放都校验持有者。 */
final class CdcLeaderLease {
  private static final DefaultRedisScript<Long> RENEW =
      CacheScripts.number("cdc-leader-renew.lua");
  private static final DefaultRedisScript<Long> UNLOCK =
      CacheScripts.number("unlock.lua");
  private final StringRedisTemplate redis;
  private final String key;
  private final String owner = UUID.randomUUID().toString();

  CdcLeaderLease(StringRedisTemplate redis, String namespace, String destination) {
    this.redis = redis;
    this.key = namespace + ":lock:cache-cdc:" + destination;
  }

  boolean tryAcquire(long leaseMillis) {
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
        key, owner, Math.max(1, leaseMillis), TimeUnit.MILLISECONDS));
  }

  boolean renew(long leaseMillis) {
    Long result = redis.execute(RENEW, Collections.singletonList(key),
        owner, String.valueOf(Math.max(1, leaseMillis)));
    return Long.valueOf(1L).equals(result);
  }

  void release() {
    redis.execute(UNLOCK, Collections.singletonList(key), owner);
  }
}
