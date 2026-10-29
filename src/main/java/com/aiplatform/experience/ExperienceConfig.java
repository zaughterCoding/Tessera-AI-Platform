package com.aiplatform.experience;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 技术体验默认关闭；启用只允许在显式本地或测试配置下。 */
@Configuration
public class ExperienceConfig {
  public ExperienceConfig(
      @Value("${app.experience.enabled:false}") boolean enabled,
      Environment environment) {
    if (!enabled) {
      return;
    }
    Set<String> profiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
    if (profiles.isEmpty()
        || !new HashSet<>(Arrays.asList("local", "test")).containsAll(profiles)) {
      throw new IllegalStateException("技术体验只允许在显式 local/test 配置下启用");
    }
  }
}
