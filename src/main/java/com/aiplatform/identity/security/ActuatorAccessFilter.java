package com.aiplatform.identity.security;

import com.aiplatform.common.api.ApiResponseWriter;
import com.aiplatform.common.api.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * 监控端点暴露面收敛：只保留规范健康探针与 Prometheus 路径。
 *
 * <p>身份和角色由 Spring Security 过滤链处理；本过滤器只负责把非规范
 * Actuator 路径统一收敛为结构化 404/405，避免暴露额外端点。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ActuatorAccessFilter extends OncePerRequestFilter {
  private static final Set<String> HEALTH = new HashSet<>(Arrays.asList(
      "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"));
  private static final String PROMETHEUS = "/actuator/prometheus";
  private final UrlPathHelper paths = new UrlPathHelper();
  private final ApiResponseWriter writer;

  public ActuatorAccessFilter(ApiResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String path = paths.getPathWithinApplication(request);
    if (!path.equals("/actuator") && !path.startsWith("/actuator/")) {
      chain.doFilter(request, response);
      return;
    }
    boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
    if (HEALTH.contains(path) && read) {
      chain.doFilter(request, response);
      return;
    }
    if (!PROMETHEUS.equals(path)) {
      writer.reject(response, ErrorCode.NOT_FOUND);
      return;
    }
    if (!read) {
      writer.reject(response, ErrorCode.METHOD_NOT_ALLOWED);
      return;
    }
    chain.doFilter(request, response);
  }
}
