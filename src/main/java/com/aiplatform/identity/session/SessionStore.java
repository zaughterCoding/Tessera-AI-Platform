package com.aiplatform.identity.session;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import java.security.SecureRandom;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final DefaultRedisScript<Long> CREATE = RedisScripts.number("create-session.lua");
  private static final DefaultRedisScript<String> REFRESH = new DefaultRedisScript<>();

  static {
    REFRESH.setLocation(new ClassPathResource("refresh-session.lua"));
    REFRESH.setResultType(String.class);
  }

  private final StringRedisTemplate redis;
  private final String prefix;
  private final int ttl;

  public SessionStore(StringRedisTemplate redis, @Value("${app.redis.key-prefix}") String prefix,
      @Value("${app.identity.session-ttl-seconds}") int ttl) {
    if (ttl < 60 || ttl > 86400) {
      throw new IllegalArgumentException("会话空闲时间必须在 60～86400 秒之间");
    }
    this.redis = redis;
    this.prefix = prefix;
    this.ttl = ttl;
  }

  public String create(Long userId) {
    for (int attempt = 0; attempt < 3; attempt++) {
      byte[] bytes = new byte[32];
      RANDOM.nextBytes(bytes);
      StringBuilder token = new StringBuilder(64);
      for (byte value : bytes) {
        token.append(String.format("%02x", value & 0xff));
      }
      if (Long.valueOf(1).equals(redis.execute(CREATE,
          Collections.singletonList(prefix + "login:token:" + token),
          userId.toString(), String.valueOf(ttl)))) {
        return token.toString();
      }
    }
    throw new ApiException(ErrorCode.UNAVAILABLE);
  }

  public Long refresh(String authorization) {
    String token = token(authorization);
    if (token == null) {
      return null;
    }
    String id = redis.execute(REFRESH,
        Collections.singletonList(prefix + "login:token:" + token), String.valueOf(ttl));
    return id == null ? null : Long.valueOf(id);
  }

  public void revoke(String authorization) {
    String token = token(authorization);
    if (token == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    redis.delete(prefix + "login:token:" + token);
  }

  private String token(String authorization) {
    if (authorization == null) {
      return null;
    }
    String value = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    return value.matches("[a-f0-9]{64}") ? value : null;
  }
}
