package com.aiplatform.identity.security;

import com.aiplatform.identity.session.RedisScripts;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {
  private static final DefaultRedisScript<Long> SCRIPT = RedisScripts.number("rate-limit.lua");
  private final StringRedisTemplate redis;
  private final String prefix;

  public RateLimiter(StringRedisTemplate redis, @Value("${app.redis.key-prefix}") String prefix) {
    this.redis = redis;
    this.prefix = prefix;
  }

  public boolean allow(String key, int limit, long windowMillis) {
    return Long.valueOf(1).equals(redis.execute(SCRIPT,
        Collections.singletonList(prefix + "limit:" + key),
        String.valueOf(limit), String.valueOf(windowMillis)));
  }
}
