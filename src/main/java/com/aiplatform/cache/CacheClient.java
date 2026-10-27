package com.aiplatform.cache;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 五层读取：请求作用域、Caffeine、Memcache、Redis、MySQL。
 *
 * <p>按 key 失效本地条目；锁 owner 与版本共同约束回填，避免旧加载污染新值。
 */
@Slf4j
@Component
public class CacheClient {
  private static final long NULL_TTL_SECONDS = 120;
  private static final int LOCAL_MAX_ENTRIES = 10_000;
  private static final int VERSION_MAX_ENTRIES = 20_000;
  private static final long MIN_AWAIT_MILLIS = 5_000;
  private static final long REDIS_FAILURE_HINT_MILLIS = 30_000;
  private static final int RETRY_BASE_MILLIS = 20;
  private static final int RETRY_JITTER_MILLIS = 20;
  private final StringRedisTemplate redis;
  private final MemcacheStore memcache;
  private final CacheFallbackGate fallback;
  private final MeterRegistry metrics;
  private final String channel;
  private final boolean enabled;
  private final long rebuildWaitMillis;
  private final long rebuildLockMillis;
  private final long redisBackoffMillis;
  private final Cache<String, CacheSnapshot> local;
  private final Cache<String, AtomicLong> versions;
  private final ConcurrentHashMap<String, CompletableFuture<CacheSnapshot>> inFlight =
      new ConcurrentHashMap<>();
  private final DefaultRedisScript<Long> fillScript;
  private final DefaultRedisScript<Long> invalidateScript;
  private final DefaultRedisScript<Long> unlockScript;
  private final AtomicLong redisDownUntil = new AtomicLong();

  public CacheClient(
      StringRedisTemplate redis,
      MemcacheStore memcache,
      CacheFallbackGate fallback,
      MeterRegistry metrics,
      @Value("${app.namespace}") String namespace,
      @Value("${app.cache.enabled:true}") boolean enabled,
      @Value("${app.cache.local-ttl-seconds:5}") long localTtlSeconds,
      @Value("${app.cache.rebuild-wait-seconds:2}") long rebuildWaitSeconds,
      @Value("${app.cache.rebuild-lock-seconds:10}") long rebuildLockSeconds,
      @Value("${app.cache.redis-backoff-ms:1000}") long redisBackoffMillis) {
    this.redis = redis;
    this.memcache = memcache;
    this.fallback = fallback;
    this.metrics = metrics;
    this.channel = namespace + ":cache:invalidate";
    this.enabled = enabled;
    this.rebuildWaitMillis = TimeUnit.SECONDS.toMillis(Math.max(1, rebuildWaitSeconds));
    this.rebuildLockMillis = TimeUnit.SECONDS.toMillis(Math.max(1, rebuildLockSeconds));
    this.redisBackoffMillis = Math.max(100, redisBackoffMillis);
    this.local = Caffeine.newBuilder()
        .maximumSize(LOCAL_MAX_ENTRIES)
        .expireAfterWrite(Math.max(1, localTtlSeconds), TimeUnit.SECONDS)
        .recordStats()
        .build();
    this.versions = Caffeine.newBuilder()
        .maximumSize(VERSION_MAX_ENTRIES)
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build();
    this.fillScript = CacheScripts.number("cache-fill.lua");
    this.invalidateScript = CacheScripts.number("cache-invalidate.lua");
    this.unlockScript = CacheScripts.number("unlock.lua");
    CaffeineCacheMetrics.monitor(metrics, local, "catalog_local");
  }

  public boolean enabled() {
    return enabled;
  }

  public CacheStats stats() {
    return local.stats();
  }

  /** 保留原教学入口，实际统一走互斥回源。 */
  public <R, ID> R queryWithPassThrough(
      String prefix,
      ID id,
      Class<R> type,
      Function<ID, R> source,
      long duration,
      TimeUnit durationUnit) {
    return queryWithMutex(prefix, id, type, source, duration, durationUnit);
  }

  public <R, ID> R queryWithMutex(
      String prefix,
      ID id,
      Class<R> type,
      Function<ID, R> source,
      long duration,
      TimeUnit durationUnit) {
    return query(prefix + id, type, () -> source.apply(id), duration, durationUnit);
  }

  public <R> R query(
      String key,
      Class<R> type,
      Supplier<R> source,
      long duration,
      TimeUnit durationUnit) {
    if (!enabled) {
      record("mysql", "load");
      return source.get();
    }
    long ttlMillis = Math.max(1, durationUnit.toMillis(duration));
    CacheSnapshot scoped = RequestCacheScope.get(key);
    if (scoped != null && scoped.fresh()) {
      record("scope", "hit");
      return decode(scoped, type);
    }
    CacheSnapshot cached = local.getIfPresent(key);
    if (cached != null && cached.fresh()) {
      record("jvm", "hit");
      RequestCacheScope.put(key, cached);
      return decode(cached, type);
    }
    if (cached != null) {
      local.invalidate(key);
    }
    record("jvm", "miss");
    long version = localVersion(key);
    CacheSnapshot loaded = loadCoalesced(key, ttlMillis, () -> serialize(source.get()));
    if (localVersion(key) != version) {
      record("fill", "fenced");
    } else {
      if (loaded.cacheable()) {
        local.put(key, loaded);
      }
      if (loaded.fresh()) {
        RequestCacheScope.put(key, loaded);
      }
    }
    return decode(loaded, type);
  }

  /**
   * 只失效目标 key 的本地条目并推进版本；Redis 中推进版本、删除数据并通知其他实例。
   *
   * @return 远端失效是否成功；本地失效始终已完成
   */
  public boolean invalidate(String key) {
    evictLocal(key);
    if (!enabled) {
      return true;
    }
    try {
      redis.execute(invalidateScript, Arrays.asList(key, key + ":version"), channel);
      return true;
    } catch (DataAccessException unavailable) {
      markRedisDown();
      log.warn("缓存远端失效失败，本地已失效，等待后续修复");
      return false;
    }
  }

  /** 逐 key 失效前缀；返回 -1 表示扫描阶段 Redis 不可用。 */
  public int invalidatePrefix(String prefix) {
    if (!enabled) {
      return 0;
    }
    Set<String> scanned;
    try {
      scanned = redis.execute((RedisCallback<Set<String>>) connection -> {
        Set<String> found = new LinkedHashSet<>();
        try (Cursor<byte[]> cursor = connection.scan(
            ScanOptions.scanOptions().match(prefix + "*").count(200).build())) {
          while (cursor.hasNext()) {
            found.add(new String(cursor.next(), StandardCharsets.UTF_8));
          }
        }
        return found;
      });
    } catch (DataAccessException unavailable) {
      markRedisDown();
      return -1;
    }
    if (scanned == null) {
      return 0;
    }
    Set<String> bases = new LinkedHashSet<>();
    for (String key : scanned) {
      bases.add(key.endsWith(":version")
          ? key.substring(0, key.length() - ":version".length()) : key);
    }
    int invalidated = 0;
    for (String key : bases) {
      if (invalidate(key)) {
        invalidated++;
      }
    }
    return invalidated;
  }

  /** 只驱逐当前实例的目标 key，供 Redis 失效通知监听器调用。 */
  public void evictLocal(String key) {
    versions.get(key, ignored -> new AtomicLong()).incrementAndGet();
    local.invalidate(key);
    RequestCacheScope.evict(key);
  }

  private CacheSnapshot loadCoalesced(String key, long ttlMillis, Supplier<String> source) {
    CompletableFuture<CacheSnapshot> mine = new CompletableFuture<>();
    CompletableFuture<CacheSnapshot> running = inFlight.putIfAbsent(key, mine);
    if (running != null) {
      record("jvm", "wait");
      return await(running);
    }
    try {
      CacheSnapshot loaded = loadLayers(key, ttlMillis, source);
      mine.complete(loaded);
      return loaded;
    } catch (RuntimeException error) {
      mine.completeExceptionally(error);
      throw error;
    } finally {
      inFlight.remove(key, mine);
    }
  }

  private CacheSnapshot await(CompletableFuture<CacheSnapshot> running) {
    long waitMillis = Math.max(MIN_AWAIT_MILLIS, rebuildWaitMillis * 2 + 2_000);
    try {
      return running.get(waitMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CacheUnavailableException("缓存加载已中断", interrupted);
    } catch (ExecutionException failed) {
      Throwable cause = failed.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new CacheUnavailableException("缓存加载失败", cause);
    } catch (TimeoutException timeout) {
      throw new CacheUnavailableException("缓存加载超时", timeout);
    }
  }

  private CacheSnapshot loadLayers(String key, long ttlMillis, Supplier<String> source) {
    if (redisDown()) {
      record("redis", "skipped");
      return degraded(ttlMillis, source);
    }
    try {
      return loadShared(key, ttlMillis, source);
    } catch (RedisUnavailable unavailable) {
      markRedisDown();
      record("redis", "error");
      return degraded(ttlMillis, source);
    }
  }

  private CacheSnapshot loadShared(String key, long ttlMillis, Supplier<String> source) {
    String revision = version(key);
    CacheSnapshot shared = memcache.get(key, revision);
    if (shared != null) {
      record("memcache", "hit");
      return shared;
    }
    record("memcache", memcache.enabled() ? "miss" : "skipped");
    CacheSnapshot cached = readRedisSnapshot(key);
    if (cached != null) {
      memcache.put(key, revision, cached);
      return cached;
    }
    return awaitOrRebuild(key, ttlMillis, source);
  }

  private CacheSnapshot awaitOrRebuild(
      String key, long ttlMillis, Supplier<String> source) {
    String lockKey = "lock:cache:" + key;
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(rebuildWaitMillis);
    do {
      String owner = UUID.randomUUID().toString();
      if (acquire(lockKey, owner)) {
        return rebuildUnderLock(key, lockKey, owner, ttlMillis, source);
      }
      String revision = version(key);
      CacheSnapshot cached = readRedisSnapshot(key);
      if (cached != null) {
        memcache.put(key, revision, cached);
        return cached;
      }
      pauseBeforeRetry();
    } while (System.nanoTime() < deadline);
    record("redis", "busy");
    throw new CacheUnavailableException("热点数据正在重建，请稍后重试");
  }

  private CacheSnapshot rebuildUnderLock(
      String key,
      String lockKey,
      String owner,
      long ttlMillis,
      Supplier<String> source) {
    try {
      String revision = version(key);
      CacheSnapshot cached = readRedisSnapshot(key);
      if (cached != null) {
        memcache.put(key, revision, cached);
        return cached;
      }
      revision = version(key);
      record("mysql", "load");
      String json = source.get();
      long ttl = json.isEmpty() ? TimeUnit.SECONDS.toMillis(NULL_TTL_SECONDS) : ttlMillis;
      ttl = Math.max(1, ttl);
      try {
        Long written = redis.execute(fillScript,
            Arrays.asList(key, lockKey, key + ":version"),
            json, String.valueOf(ttl), revision, owner);
        CacheSnapshot loaded = new CacheSnapshot(json,
            System.currentTimeMillis() + ttl, Long.valueOf(1).equals(written));
        if (loaded.cacheable()) {
          memcache.put(key, revision, loaded);
        } else {
          record("fill", "rejected");
        }
        return loaded;
      } catch (DataAccessException unavailable) {
        markRedisDown();
        record("redis", "fill-error");
        return new CacheSnapshot(json, System.currentTimeMillis() + ttl, false);
      }
    } finally {
      release(lockKey, owner);
    }
  }

  private CacheSnapshot degraded(long ttlMillis, Supplier<String> source) {
    record("fallback", "attempt");
    try (CacheFallbackGate.Permit permit = fallback.acquire()) {
      if (permit == null) {
        record("fallback", "rejected");
        throw new CacheUnavailableException("缓存服务不可用，请稍后重试");
      }
      record("fallback", "db");
      record("mysql", "load");
      String json = source.get();
      return new CacheSnapshot(json,
          System.currentTimeMillis() + Math.max(1, ttlMillis), false);
    }
  }

  private String version(String key) {
    try {
      String value = redis.opsForValue().get(key + ":version");
      return value == null ? "0" : value;
    } catch (DataAccessException unavailable) {
      throw new RedisUnavailable(unavailable);
    }
  }

  private CacheSnapshot readRedisSnapshot(String key) {
    String json;
    Long ttlMillis;
    try {
      json = redis.opsForValue().get(key);
      if (json == null) {
        record("redis", "miss");
        return null;
      }
      ttlMillis = redis.getExpire(key, TimeUnit.MILLISECONDS);
    } catch (DataAccessException unavailable) {
      throw new RedisUnavailable(unavailable);
    }
    if (!isUsableCachePayload(json)) {
      record("redis", "corrupt");
      return null;
    }
    if (ttlMillis != null && ttlMillis == -2) {
      record("redis", "miss");
      return null;
    }
    long remaining = ttlMillis == null || ttlMillis < 0 ? REDIS_FAILURE_HINT_MILLIS : ttlMillis;
    record("redis", "hit");
    return new CacheSnapshot(json, System.currentTimeMillis() + remaining, true);
  }

  private boolean acquire(String lockKey, String owner) {
    try {
      return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
          lockKey, owner, rebuildLockMillis, TimeUnit.MILLISECONDS));
    } catch (DataAccessException unavailable) {
      throw new RedisUnavailable(unavailable);
    }
  }

  private void release(String lockKey, String owner) {
    try {
      redis.execute(unlockScript, Collections.singletonList(lockKey), owner);
    } catch (DataAccessException unavailable) {
      markRedisDown();
    }
  }

  private void pauseBeforeRetry() {
    try {
      Thread.sleep(RETRY_BASE_MILLIS + ThreadLocalRandom.current().nextInt(RETRY_JITTER_MILLIS));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CacheUnavailableException("缓存查询已中断", interrupted);
    }
  }

  private long localVersion(String key) {
    AtomicLong version = versions.getIfPresent(key);
    return version == null ? 0 : version.get();
  }

  private boolean redisDown() {
    return System.currentTimeMillis() < redisDownUntil.get();
  }

  private void markRedisDown() {
    long now = System.currentTimeMillis();
    long previous = redisDownUntil.getAndSet(now + redisBackoffMillis);
    if (previous <= now) {
      log.warn("Redis 读取失败，公开读进入有界降级窗口 {} ms", redisBackoffMillis);
    }
  }

  private void record(String layer, String result) {
    CacheReadTrace.record(layer, result);
    metrics.counter("aiplatform.cache.requests",
        "layer", layer, "result", result).increment();
  }

  private <R> R decode(CacheSnapshot snapshot, Class<R> type) {
    if (snapshot.empty()) {
      return null;
    }
    JSONObject object = JSONUtil.parseObj(snapshot.json());
    if (object.containsKey("expireTime") && object.containsKey("data")) {
      return JSONUtil.toBean(object.getJSONObject("data"), type);
    }
    return JSONUtil.toBean(object, type);
  }

  private String serialize(Object value) {
    return value == null ? "" : JSONUtil.toJsonStr(value);
  }

  private boolean isUsableCachePayload(String json) {
    if (json.isEmpty()) {
      return true;
    }
    try {
      JSONObject object = JSONUtil.parseObj(json);
      return !object.containsKey("expireTime")
          || LocalDateTime.parse(object.getStr("expireTime")).isAfter(LocalDateTime.now());
    } catch (RuntimeException corrupt) {
      return false;
    }
  }

  private static final class RedisUnavailable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private RedisUnavailable(Throwable cause) {
      super(cause);
    }
  }
}
