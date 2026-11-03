package com.aiplatform.generation.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** 提交途中租约过期会被恢复流程接管，已出的结果写不回去，任务只能转人工核对，所以启动时就拒绝。 */
@Component
public class GenerationLeaseGuard {
  private final GenerationProperties generation;
  private final HttpProviderProperties http;

  public GenerationLeaseGuard(GenerationProperties generation, HttpProviderProperties http) {
    this.generation = generation;
    this.http = http;
  }

  @PostConstruct
  public void validate() {
    if (http.isEnabled() && generation.getLeaseSeconds() * 1000L <= http.getRequestTimeoutMs()) {
      throw new IllegalArgumentException("生成认领租约必须长于真实渠道请求超时");
    }
  }
}
