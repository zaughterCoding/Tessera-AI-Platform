package com.aiplatform.campaign;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 订单号在受理前生成，按秒时间戳与当日序列组合，Redis 自增保证进程内并发唯一。 */
@Component
public class RedisIdWorker {
  private static final long EPOCH_SECONDS = 1640995200L;
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy:MM:dd");
  private final StringRedisTemplate redis;
  private final String prefix;

  public RedisIdWorker(
      StringRedisTemplate redis, @Value("${app.redis.key-prefix}") String prefix) {
    this.redis = redis;
    this.prefix = prefix;
  }

  public long nextOrderId() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    String key = prefix + "icr:campaign-order:" + today.format(DAY);
    Long sequence = redis.opsForValue().increment(key);
    if (sequence == null) {
      throw new IllegalStateException("无法生成预约订单号");
    }
    if (sequence == 1L) {
      redis.expire(key, 2, TimeUnit.DAYS);
    }
    long seconds = System.currentTimeMillis() / 1000 - EPOCH_SECONDS;
    return (seconds << 32) | (sequence & 0xFFFFFFFFL);
  }
}
