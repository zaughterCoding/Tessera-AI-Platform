package com.aiplatform.identity.security;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ApiResponseWriter;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.service.IUserService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MetricsAccessFilter extends OncePerRequestFilter {
  public static final String AUTHORIZED = MetricsAccessFilter.class.getName() + ".authorized";
  private static final Set<String> HEALTH = new HashSet<>(Arrays.asList(
      "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"));
  private final UrlPathHelper paths = new UrlPathHelper();
  private final IUserService users;
  private final ApiResponseWriter writer;
  private final String monitorToken;

  public MetricsAccessFilter(IUserService users, ApiResponseWriter writer,
      @Value("${app.identity.monitor-token}") String monitorToken) {
    this.users = users;
    this.writer = writer;
    this.monitorToken = monitorToken;
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
      request.setAttribute(AUTHORIZED, true);
      chain.doFilter(request, response);
      return;
    }
    if (!"/actuator/prometheus".equals(path)) {
      writer.reject(response, ErrorCode.NOT_FOUND);
      return;
    }
    if (!read) {
      writer.reject(response, ErrorCode.METHOD_NOT_ALLOWED);
      return;
    }
    String authorization = request.getHeader("Authorization");
    boolean monitor = !monitorToken.isEmpty() && authorization != null
        && MessageDigest.isEqual(("Bearer " + monitorToken).getBytes(StandardCharsets.UTF_8),
            authorization.getBytes(StandardCharsets.UTF_8));
    try {
      UserDTO user = monitor ? null : users.authenticate(authorization);
      if (!monitor && user == null) {
        writer.reject(response, ErrorCode.UNAUTHENTICATED);
        return;
      }
      if (!monitor && !user.getRoles().contains("ADMIN")) {
        writer.reject(response, ErrorCode.FORBIDDEN);
        return;
      }
    } catch (ApiException error) {
      writer.reject(response, error.getErrorCode());
      return;
    } catch (DataAccessException unavailable) {
      writer.reject(response, ErrorCode.UNAVAILABLE);
      return;
    }
    request.setAttribute(AUTHORIZED, true);
    chain.doFilter(request, response);
  }
}
