package com.aiplatform.common.config;

import com.aiplatform.identity.security.AccessGuard;
import com.aiplatform.operations.audit.OperationAuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
  private final AccessGuard access;
  private final OperationAuditInterceptor audit;

  public MvcConfig(AccessGuard access, OperationAuditInterceptor audit) {
    this.access = access;
    this.audit = audit;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(access).addPathPatterns("/**");
    // 访问守卫先完成身份与角色校验；审计只处理守卫放行后的管理写操作。
    registry.addInterceptor(audit)
        .addPathPatterns("/admin/**", "/operations/**")
        .order(1);
  }
}
