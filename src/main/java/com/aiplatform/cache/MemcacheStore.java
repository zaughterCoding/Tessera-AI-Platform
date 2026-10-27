package com.aiplatform.cache;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PreDestroy;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.FailureMode;
import net.spy.memcached.MemcachedClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 版本化 Memcache 层；故障时快速退避，不能阻塞 Redis 或数据库路径。 */
@Component
public class MemcacheStore {
  private static final long MAX_TTL_MILLIS = 30_000;
  private static final long RETRY_BACKOFF_MILLIS = 1_000;
  private final boolean enabled;
  private final String host;
  private final int port;
  private final long timeoutMillis;
  private final String namespace;
  private final MeterRegistry metrics;
  private final AtomicLong retryAfter = new AtomicLong();
  private volatile MemcachedClient client;

  public MemcacheStore(
      @Value("${app.cache.memcache.enabled:false}") boolean enabled,
      @Value("${app.cache.memcache.host:127.0.0.1}") String host,
      @Value("${app.cache.memcache.port:11211}") int port,
      @Value("${app.cache.memcache.timeout-ms:100}") long timeoutMillis,
      @Value("${app.namespace}") String namespace,
      MeterRegistry metrics) {
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.timeoutMillis = Math.max(10, timeoutMillis);
    this.namespace = namespace;
    this.metrics = metrics;
  }

  public boolean enabled() {
    return enabled;
  }

  public String key(String key, String version) {
    return namespace + ":mc:" + DigestUtil.sha256Hex(key) + ":" + version;
  }

  private MemcachedClient connection() throws IOException {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = new MemcachedClient(
              new ConnectionFactoryBuilder()
                  .setOpTimeout(timeoutMillis)
                  .setFailureMode(FailureMode.Cancel)
                  .setOpQueueMaxBlockTime(timeoutMillis)
                  .setDaemon(true)
                  .build(),
              Collections.singletonList(new InetSocketAddress(host, port)));
        }
      }
    }
    return client;
  }

  private void count(String operation, String result) {
    metrics.counter("aiplatform.memcache.operations",
        "operation", operation, "result", result).increment();
  }

  private boolean available() {
    return enabled && System.currentTimeMillis() >= retryAfter.get();
  }

  public CacheSnapshot get(String key, String version) {
    if (!available()) {
      return null;
    }
    Future<Object> future = null;
    try {
      future = connection().asyncGet(key(key, version));
      Object raw = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
      if (raw == null) {
        count("get", "miss");
        return null;
      }
      JSONObject value = JSONUtil.parseObj(raw.toString());
      String json = value.getStr("json");
      CacheSnapshot snapshot = new CacheSnapshot(json, value.getLong("expiresAt", 0L), true);
      if (json == null || !snapshot.fresh()) {
        count("get", "expired");
        return null;
      }
      count("get", "hit");
      return snapshot;
    } catch (Exception error) {
      if (future != null) {
        future.cancel(false);
      }
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      retryAfter.set(System.currentTimeMillis() + RETRY_BACKOFF_MILLIS);
      count("get", "error");
      return null;
    }
  }

  public boolean put(String key, String version, CacheSnapshot snapshot) {
    if (!available() || !snapshot.cacheable() || !snapshot.fresh()) {
      return false;
    }
    Future<Boolean> future = null;
    try {
      long now = System.currentTimeMillis();
      long remaining = Math.min(MAX_TTL_MILLIS, snapshot.expiresAt() - now);
      if (remaining <= 0) {
        return false;
      }
      String envelope = JSONUtil.createObj()
          .set("json", snapshot.json())
          .set("expiresAt", Math.min(snapshot.expiresAt(), now + MAX_TTL_MILLIS))
          .toString();
      int seconds = (int) Math.max(1, (remaining + 999) / 1000);
      future = connection().set(key(key, version), seconds, envelope);
      boolean stored = Boolean.TRUE.equals(future.get(timeoutMillis, TimeUnit.MILLISECONDS));
      count("put", stored ? "success" : "error");
      return stored;
    } catch (Exception error) {
      if (future != null) {
        future.cancel(false);
      }
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      retryAfter.set(System.currentTimeMillis() + RETRY_BACKOFF_MILLIS);
      count("put", "error");
      return false;
    }
  }

  @PreDestroy
  public void close() {
    MemcachedClient current = client;
    if (current != null) {
      current.shutdown();
    }
  }
}
