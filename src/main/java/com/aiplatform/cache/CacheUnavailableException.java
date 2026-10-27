package com.aiplatform.cache;

/** 缓存重建或降级回源达到保护上限时快速失败，由接口层转换为 503。 */
public class CacheUnavailableException extends RuntimeException {
  public CacheUnavailableException(String message) {
    super(message);
  }

  public CacheUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
