package com.aiplatform.cache;

import java.util.HashMap;
import java.util.Map;

/** 请求内缓存作用域，不允许把请求数据带进后台线程或复用线程。 */
public final class RequestCacheScope implements AutoCloseable {
  private static final int MAX_ENTRIES = 256;
  private static final ThreadLocal<Map<String, CacheSnapshot>> CURRENT = new ThreadLocal<>();
  private final Map<String, CacheSnapshot> previous;

  private RequestCacheScope() {
    previous = CURRENT.get();
    CURRENT.set(new HashMap<>());
  }

  public static RequestCacheScope open() {
    return new RequestCacheScope();
  }

  public static CacheSnapshot get(String key) {
    Map<String, CacheSnapshot> values = CURRENT.get();
    return values == null ? null : values.get(key);
  }

  public static void put(String key, CacheSnapshot value) {
    Map<String, CacheSnapshot> values = CURRENT.get();
    if (values != null && values.size() < MAX_ENTRIES) {
      values.put(key, value);
    }
  }

  public static void evict(String key) {
    Map<String, CacheSnapshot> values = CURRENT.get();
    if (values != null) {
      values.remove(key);
    }
  }

  public static boolean active() {
    return CURRENT.get() != null;
  }

  @Override
  public void close() {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }
}
