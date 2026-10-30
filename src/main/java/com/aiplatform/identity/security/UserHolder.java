package com.aiplatform.identity.security;

import com.aiplatform.identity.dto.UserDTO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 业务代码的身份入口，直接读写 Spring Security 上下文。
 *
 * <p>不再维护独立 ThreadLocal，避免过滤器、拦截器与服务层看到不同身份。
 * 请求结束时由上下文过滤器清理，复用线程不会遗留上一请求的身份。
 */
public final class UserHolder {
  private UserHolder() {}

  public static UserDTO getUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof UserDTO) {
      return (UserDTO) authentication.getPrincipal();
    }
    return null;
  }

  public static void saveUser(UserDTO user) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    if (user.getRoles() != null) {
      for (String role : user.getRoles()) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
      }
    }
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(user, null, authorities));
  }

  public static void removeUser() {
    SecurityContextHolder.clearContext();
  }
}
