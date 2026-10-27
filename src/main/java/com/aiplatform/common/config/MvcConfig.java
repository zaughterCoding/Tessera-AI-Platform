package com.aiplatform.common.config;

import com.aiplatform.identity.security.AccessGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
  private final AccessGuard access;

  public MvcConfig(AccessGuard access) {
    this.access = access;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(access).addPathPatterns("/**");
  }
}
