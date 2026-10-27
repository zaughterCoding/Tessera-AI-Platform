package com.aiplatform.cache;

/** 不可变的缓存快照；空字符串表示已缓存的空结果。 */
public final class CacheSnapshot {
  private final String json;
  private final long expiresAt;
  private final boolean cacheable;

  public CacheSnapshot(String json, long expiresAt, boolean cacheable) {
    this.json = json;
    this.expiresAt = expiresAt;
    this.cacheable = cacheable;
  }

  public String json() {
    return json;
  }

  public long expiresAt() {
    return expiresAt;
  }

  public boolean cacheable() {
    return cacheable;
  }

  public boolean empty() {
    return json.isEmpty();
  }

  public boolean fresh() {
    return expiresAt > System.currentTimeMillis();
  }
}
