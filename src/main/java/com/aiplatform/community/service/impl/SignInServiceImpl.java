package com.aiplatform.community.service.impl;

import com.aiplatform.cache.CacheScripts;
import com.aiplatform.community.CommunityConfig;
import com.aiplatform.community.dto.SignInResult;
import com.aiplatform.community.service.ISignInService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 签到只记录自然日：业务时区决定日期键，按位记录当月日期，
 * 连续天数从今天向前数且不跨月；不发放任何权益或余额。
 */
@Service
public class SignInServiceImpl implements ISignInService {
  private static final DefaultRedisScript<Long> SIGN =
      CacheScripts.number("sign-in.lua");
  private static final DefaultRedisScript<Long> COUNT =
      CacheScripts.number("sign-in-count.lua");
  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

  private final StringRedisTemplate redis;
  private final CommunityConfig config;
  private final String prefix;

  public SignInServiceImpl(
      StringRedisTemplate redis,
      CommunityConfig config,
      @Value("${app.redis.key-prefix}") String prefix) {
    this.redis = redis;
    this.config = config;
    this.prefix = prefix;
  }

  @Override
  public SignInResult signIn(long userId) {
    LocalDate today = LocalDate.now(config.signInZone());
    String key = key(userId, today);
    Long prior = redis.execute(SIGN, Collections.singletonList(key),
        String.valueOf(today.getDayOfMonth() - 1),
        String.valueOf(config.signInTtlSeconds()));
    if (prior == null) {
      throw new IllegalStateException("签到写入未完成");
    }
    return new SignInResult(today, prior == 1L, consecutiveDays(userId));
  }

  @Override
  public SignInResult status(long userId) {
    LocalDate today = LocalDate.now(config.signInZone());
    String key = key(userId, today);
    Boolean signed = redis.opsForValue().getBit(key, today.getDayOfMonth() - 1);
    return new SignInResult(today, Boolean.TRUE.equals(signed), consecutiveDays(userId));
  }

  @Override
  public int consecutiveDays(long userId) {
    LocalDate today = LocalDate.now(config.signInZone());
    Long days = redis.execute(COUNT, Collections.singletonList(key(userId, today)),
        String.valueOf(today.getDayOfMonth() - 1));
    return days == null ? 0 : days.intValue();
  }

  private String key(long userId, LocalDate date) {
    return prefix + "community:signin:" + userId + ":" + date.format(MONTH);
  }
}
