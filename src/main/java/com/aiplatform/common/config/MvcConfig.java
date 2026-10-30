package com.aiplatform.common.config;

import com.aiplatform.operations.audit.OperationAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
  private final OperationAuditInterceptor audit;

  public MvcConfig(OperationAuditInterceptor audit) {
    this.audit = audit;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // 身份与角色由 Spring Security 过滤链完成；审计只处理放行后的管理写操作。
    registry.addInterceptor(audit)
        .addPathPatterns("/admin/**", "/operations/**")
        .order(1);
  }
}
