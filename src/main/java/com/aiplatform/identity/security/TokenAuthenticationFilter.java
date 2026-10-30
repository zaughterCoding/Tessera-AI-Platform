package com.aiplatform.identity.security;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ApiResponseWriter;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.service.IUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.UrlPathHelper;

/**
 * 建立请求身份：会话令牌、监控令牌与公开入口的可选身份。
 *
 * <p>会话仍是 Redis 中的不透明令牌，不引入 JWT、表单登录或 Cookie 登录。
 * 监控令牌只在 Prometheus 路径生效，且只授予 METRICS 权限，不会成为 ADMIN。
 */
@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {
  private static final Set<String> HEALTH = new HashSet<>(Arrays.asList(
      "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"));
  private static final String PROMETHEUS = "/actuator/prometheus";
  private static final String METRICS_AUTHORITY = "METRICS";
  private final IUserService users;
  private final EndpointSecurityResolver endpoints;
  private final RateLimiter limits;
  private final ApiResponseWriter writer;
  private final String monitorToken;
  private final UrlPathHelper paths = new UrlPathHelper();

  public TokenAuthenticationFilter(IUserService users, EndpointSecurityResolver endpoints,
      RateLimiter limits, ApiResponseWriter writer, String monitorToken) {
    this.users = users;
    this.endpoints = endpoints;
    this.limits = limits;
    this.writer = writer;
    this.monitorToken = monitorToken;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String path = paths.getPathWithinApplication(request);
    if (HEALTH.contains(path)) {
      chain.doFilter(request, response);
      return;
    }
    // 监控令牌只对原始规范路径生效，编码、合并分隔符等变体不得提升为指标权限。
    if (PROMETHEUS.equals(rawPath(request))) {
      authenticateMetrics(request, response, chain);
      return;
    }
    HandlerMethod endpoint = endpoints.resolve(request);
    if (endpoint == null) {
      chain.doFilter(request, response);
      return;
    }
    if (endpoints.isPublic(endpoint)) {
      if (endpoints.isRateLimited(endpoint) && !withinRateLimit(request, response)) {
        return;
      }
      attachOptional(request);
      chain.doFilter(request, response);
      return;
    }
    authenticateSession(request, response, chain);
  }

  private void authenticateMetrics(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (isMonitorToken(authorization)) {
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken("monitor", null,
              List.of(new SimpleGrantedAuthority(METRICS_AUTHORITY))));
      chain.doFilter(request, response);
      return;
    }
    authenticateSession(request, response, chain);
  }

  private void authenticateSession(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    UserDTO user;
    try {
      user = users.authenticate(request.getHeader("Authorization"));
    } catch (ApiException error) {
      writer.reject(response, error.getErrorCode());
      return;
    } catch (DataAccessException unavailable) {
      writer.reject(response, ErrorCode.UNAVAILABLE);
      return;
    } catch (RuntimeException failure) {
      // 不记录令牌、请求体或异常详情，仅按请求编号关联诊断。
      log.error("身份校验失败，requestId={}，type={}", MDC.get("requestId"),
          failure.getClass().getName());
      writer.reject(response, ErrorCode.INTERNAL_ERROR);
      return;
    }
    if (user != null) {
      UserHolder.saveUser(user);
    }
    // 链下游的授权/业务异常交给 Spring Security 与 MVC 转换，不能被身份过滤器吞掉。
    chain.doFilter(request, response);
  }

  private boolean withinRateLimit(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    try {
      if (limits.allow("auth-ip:" + request.getRemoteAddr(), 30, 60000)) {
        return true;
      }
      writer.reject(response, ErrorCode.TOO_MANY_REQUESTS);
      return false;
    } catch (DataAccessException unavailable) {
      writer.reject(response, ErrorCode.UNAVAILABLE);
      return false;
    }
  }

  private void attachOptional(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization == null || authorization.trim().isEmpty()) {
      return;
    }
    try {
      UserDTO user = users.authenticate(authorization);
      if (user != null) {
        UserHolder.saveUser(user);
      }
    } catch (RuntimeException failure) {
      // 公开接口保持匿名可用，凭据识别失败不得改变访问结果。
      log.debug("公开接口可选身份识别失败，type={}", failure.getClass().getSimpleName());
    }
  }

  private String rawPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String context = request.getContextPath();
    if (context != null && !context.isEmpty() && uri.startsWith(context)) {
      uri = uri.substring(context.length());
    }
    return uri == null ? "" : uri;
  }

  private boolean isMonitorToken(String authorization) {
    return !monitorToken.isEmpty() && authorization != null
        && MessageDigest.isEqual(("Bearer " + monitorToken).getBytes(StandardCharsets.UTF_8),
            authorization.getBytes(StandardCharsets.UTF_8));
  }
}
