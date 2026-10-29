package com.aiplatform.operations.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 只上报真实可探测的依赖；尚未探测过的依赖保持未知而不是假装可用。 */
@Slf4j
@Component
public class DependencyHealthMetrics {
  private final DataSource dataSource;
  private final StringRedisTemplate redis;
  private final AtomicInteger mysql = new AtomicInteger(-1);
  private final AtomicInteger redisUp = new AtomicInteger(-1);

  public DependencyHealthMetrics(
      DataSource dataSource, StringRedisTemplate redis, MeterRegistry metrics) {
    this.dataSource = dataSource;
    this.redis = redis;
    gauge(metrics, "mysql", mysql);
    gauge(metrics, "redis", redisUp);
  }

  @Scheduled(fixedDelayString = "${app.operations.dependency-check-interval-ms:15000}",
      initialDelayString = "${app.operations.dependency-check-initial-delay-ms:5000}")
  public void check() {
    checkMysql();
    checkRedis();
  }

  private void checkMysql() {
    try (Connection connection = dataSource.getConnection()) {
      mysql.set(connection.isValid(2) ? 1 : 0);
    } catch (Exception failure) {
      mysql.set(0);
      log.warn("MySQL 依赖探测失败，type={}", failure.getClass().getSimpleName());
    }
  }

  private void checkRedis() {
    try {
      Boolean pong = redis.execute((RedisCallback<Boolean>) connection ->
          "PONG".equalsIgnoreCase(connection.ping()));
      redisUp.set(Boolean.TRUE.equals(pong) ? 1 : 0);
    } catch (RuntimeException failure) {
      redisUp.set(0);
      log.warn("Redis 依赖探测失败，type={}", failure.getClass().getSimpleName());
    }
  }

  private void gauge(MeterRegistry registry, String dependency, AtomicInteger value) {
    Gauge.builder("aiplatform.dependency.up", value,
        holder -> holder.get() < 0 ? Double.NaN : holder.get())
        .tag("dependency", dependency)
        .register(registry);
  }
}
