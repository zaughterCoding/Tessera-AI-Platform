package com.aiplatform.generation.provider;

/** 上游明确限流：这次提交没有被接受，可以安全重新排队；响应丢失或超时不属于这一类。 */
public class ProviderThrottledException extends RuntimeException {
  private final Integer retryAfterSeconds;

  public ProviderThrottledException(Integer retryAfterSeconds) {
    super("上游限流");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  /** 上游建议的等待秒数；没有给出时为空。 */
  public Integer retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
