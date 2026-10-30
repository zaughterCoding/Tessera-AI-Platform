package com.aiplatform.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 统一解析当前请求的控制器方法及其访问标记。
 *
 * <p>请求权限与令牌过滤器共用同一解析结果，避免两套身份判断出现分歧。
 * 无匹配方法或方法不支持时不在此处拒绝，仍由 MVC 生成既有的 404/405 响应。
 */
@Component
public class EndpointSecurityResolver {
  private static final String HANDLER_ATTRIBUTE =
      EndpointSecurityResolver.class.getName() + ".handler";
  private static final Object NONE = new Object();
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;

  public EndpointSecurityResolver(
      @Qualifier("requestMappingHandlerMapping")
      ObjectProvider<RequestMappingHandlerMapping> mappings) {
    this.mappings = mappings;
  }

  public HandlerMethod resolve(HttpServletRequest request) {
    Object cached = request.getAttribute(HANDLER_ATTRIBUTE);
    if (cached == NONE) {
      return null;
    }
    if (cached instanceof HandlerMethod) {
      return (HandlerMethod) cached;
    }
    HandlerMethod handler = lookup(request);
    request.setAttribute(HANDLER_ATTRIBUTE, handler == null ? NONE : handler);
    return handler;
  }

  private HandlerMethod lookup(HttpServletRequest request) {
    RequestMappingHandlerMapping mapping = mappings.getIfAvailable();
    if (mapping == null) {
      return null;
    }
    try {
      HandlerExecutionChain chain = mapping.getHandler(request);
      if (chain != null && chain.getHandler() instanceof HandlerMethod) {
        return (HandlerMethod) chain.getHandler();
      }
    } catch (Exception malformedOrUnsupported) {
      // 由 MVC 统一处理无匹配处理器与错误方法，保持原有结构化 404/405。
    }
    return null;
  }

  public boolean isPublic(HandlerMethod handler) {
    return publicMarker(handler) != null;
  }

  public boolean isRateLimited(HandlerMethod handler) {
    PublicEndpoint marker = publicMarker(handler);
    return marker != null && marker.rateLimited();
  }

  public boolean requiresAdmin(HandlerMethod handler) {
    return handler.hasMethodAnnotation(RequireAdmin.class)
        || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RequireAdmin.class);
  }

  private PublicEndpoint publicMarker(HandlerMethod handler) {
    PublicEndpoint marker = handler.getMethodAnnotation(PublicEndpoint.class);
    if (marker == null) {
      marker = AnnotatedElementUtils.findMergedAnnotation(
          handler.getBeanType(), PublicEndpoint.class);
    }
    return marker;
  }
}
