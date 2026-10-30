package com.aiplatform.identity.security;

import com.aiplatform.common.api.ApiResponseWriter;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.identity.service.IUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * 无状态令牌认证的过滤链。
 *
 * <p>会话令牌由 Redis 维护，前端使用 Authorization 头且不使用 Cookie，
 * 因此关闭 CSRF、表单登录、HTTP Basic、默认登出和请求缓存；CORS 维持
 * 同源部署的既有行为。认证/授权失败统一输出原有的 JSON 协议。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  public SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      IUserService users,
      EndpointSecurityResolver endpoints,
      EndpointAuthorizationManager authorization,
      RateLimiter limits,
      ApiResponseWriter writer,
      @Value("${app.identity.monitor-token}") String monitorToken) throws Exception {
    TokenAuthenticationFilter tokens =
        new TokenAuthenticationFilter(users, endpoints, limits, writer, monitorToken);
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(cache -> cache.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, failure) ->
                writer.reject(response, ErrorCode.UNAUTHENTICATED))
            .accessDeniedHandler((request, response, failure) ->
                writer.reject(response, ErrorCode.FORBIDDEN)))
        .authorizeHttpRequests(requests -> requests
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers("/actuator/prometheus").hasAnyAuthority("METRICS", "ROLE_ADMIN")
            .requestMatchers("/actuator", "/actuator/**").denyAll()
            .requestMatchers("/error").permitAll()
            .anyRequest().access(authorization))
        .addFilterBefore(tokens, AuthorizationFilter.class);
    return http.build();
  }
}
