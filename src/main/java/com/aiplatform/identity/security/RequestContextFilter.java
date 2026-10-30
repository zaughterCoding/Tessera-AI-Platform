package com.aiplatform.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("identityContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UserHolder.removeUser();
    String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    request.setAttribute("requestId", requestId);
    response.setHeader("X-Request-ID", requestId);
    response.setHeader("Cache-Control", "no-store");
    try {
      chain.doFilter(request, response);
    } finally {
      // 即使拦截器拒绝或控制器异常，也不能向复用线程遗留身份。
      UserHolder.removeUser();
      MDC.remove("requestId");
    }
  }
}
