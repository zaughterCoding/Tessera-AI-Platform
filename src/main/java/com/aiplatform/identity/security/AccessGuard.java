package com.aiplatform.identity.security;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.service.IUserService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AccessGuard implements HandlerInterceptor {
  private final IUserService users;
  private final RateLimiter limits;

  public AccessGuard(IUserService users, RateLimiter limits) {
    this.users = users;
    this.limits = limits;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod)) {
      return true;
    }
    HandlerMethod method = (HandlerMethod) handler;
    boolean actuator = method.getBeanType().getName()
        .startsWith("org.springframework.boot.actuate.");
    if (actuator && Boolean.TRUE.equals(request.getAttribute(MetricsAccessFilter.AUTHORIZED))) {
      return true;
    }
    boolean admin = method.hasMethodAnnotation(RequireAdmin.class)
        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequireAdmin.class)
        || actuator;
    PublicEndpoint open = method.getMethodAnnotation(PublicEndpoint.class);
    if (open == null) {
      open = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), PublicEndpoint.class);
    }
    if (open != null && !admin) {
      if (open.rateLimited() && !limits.allow("auth-ip:" + request.getRemoteAddr(), 30, 60000)) {
        throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
      }
      return true;
    }
    UserDTO user = users.authenticate(request.getHeader("Authorization"));
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    UserHolder.saveUser(user);
    if (admin && !user.getRoles().contains("ADMIN")) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    return true;
  }
}
