package com.aiplatform.generation.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 模拟故障注入只允许在显式 local/test 配置下存在；布尔开关本身不构成环境授权。
 * 正式、默认及混合正式 profile 在启动时直接拒绝，服务边界再次校验。
 */
@Component
public class SimulatorFaultControlPolicy {
  private static final Set<String> ALLOWED_PROFILES = Set.of("local", "test");

  private final Environment environment;

  public SimulatorFaultControlPolicy(GenerationProperties properties, Environment environment) {
    this.environment = environment;
    if (properties.isSimulatorFaultControlEnabled() && !allowed()) {
      throw new IllegalStateException("模拟故障控制只允许在显式 local/test 配置下启用");
    }
  }

  public boolean allowed() {
    Set<String> profiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
    return !profiles.isEmpty() && ALLOWED_PROFILES.containsAll(profiles);
  }
}
