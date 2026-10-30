package com.aiplatform.identity.security;

import java.util.function.Supplier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * 请求级授权：管理入口要求 ADMIN，公开入口放行，其余控制器方法要求已认证。
 *
 * <p>没有控制器方法时放行，让 MVC 继续产生原有的 404/405 响应；令牌校验与
 * 方法级授权分别负责身份建立和实际服务边界。
 */
@Component
public class EndpointAuthorizationManager
    implements AuthorizationManager<RequestAuthorizationContext> {
  private final EndpointSecurityResolver endpoints;

  public EndpointAuthorizationManager(EndpointSecurityResolver endpoints) {
    this.endpoints = endpoints;
  }

  @Override
  public AuthorizationResult authorize(
      Supplier<? extends Authentication> authentication,
      RequestAuthorizationContext context) {
    HandlerMethod handler = endpoints.resolve(context.getRequest());
    if (handler == null) {
      return new AuthorizationDecision(true);
    }
    Authentication current = authentication.get();
    if (endpoints.requiresAdmin(handler)) {
      return new AuthorizationDecision(hasRole(current, "ROLE_ADMIN"));
    }
    if (endpoints.isPublic(handler)) {
      return new AuthorizationDecision(true);
    }
    return new AuthorizationDecision(isAuthenticated(current));
  }

  private boolean isAuthenticated(Authentication authentication) {
    return authentication != null && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }

  private boolean hasRole(Authentication authentication, String role) {
    if (!isAuthenticated(authentication)) {
      return false;
    }
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (role.equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }
}
