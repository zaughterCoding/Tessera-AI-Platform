package com.aiplatform.generation.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 真实图片渠道的 HTTP 配置；默认关闭，未配置凭据或地址时不得发起任何外部调用。
 * 真实调用次数上限默认 0，只有显式设置正数并按次扣减后才允许提交付费请求。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.generation.http")
public class HttpProviderProperties {
  public static final String PROTOCOL_GENERIC = "generic";
  public static final String PROTOCOL_SILICONFLOW = "siliconflow";
  private static final Set<String> TEST_PROFILES = Set.of("local", "test");

  private boolean enabled;
  /** generic 为通用提交/查询协议；siliconflow 为同步图片接口。 */
  private String protocol = PROTOCOL_GENERIC;
  private String baseUrl = "";
  private String submitPath = "/v1/images/jobs";
  private String taskPath = "/v1/images/jobs/{taskId}";
  private String cancelPath = "";
  private String model = "";
  private String apiKey = "";
  private String authScheme = "Bearer";
  private boolean allowInsecureHttp;
  private int realCallLimit;
  private int connectTimeoutMs = 3000;
  private int requestTimeoutMs = 25000;
  private int queryTimeoutMs = 10000;
  private int maxResponseBytes = 262144;
  private boolean idempotentSubmit;
  private boolean queryByAttempt;
  /** 渠道展示名，仅用于状态接口，不含供应商凭据或内部地址。 */
  private String displayName = "HTTP 图片渠道";

  @Autowired
  private Environment environment;

  @PostConstruct
  public void validate() {
    if (!PROTOCOL_GENERIC.equals(protocol) && !PROTOCOL_SILICONFLOW.equals(protocol)) {
      throw new IllegalArgumentException("真实渠道协议只支持 generic 或 siliconflow");
    }
    requireRange("connect-timeout-ms", connectTimeoutMs, 100, 30000);
    requireRange("request-timeout-ms", requestTimeoutMs, 500, 120000);
    requireRange("query-timeout-ms", queryTimeoutMs, 500, 60000);
    requireRange("max-response-bytes", maxResponseBytes, 1024, 4_000_000);
    requireRange("real-call-limit", realCallLimit, 0, 100000);
    requirePath("submit-path", submitPath);
    requirePath("task-path", taskPath);
    if (!taskPath.contains("{taskId}")) {
      throw new IllegalArgumentException("真实渠道任务查询路径必须包含 {taskId}");
    }
    if (!cancelPath.isBlank()) {
      requirePath("cancel-path", cancelPath);
    }
    if (enabled) {
      requireBaseUrl();
      if (apiKey == null || apiKey.length() < 8) {
        throw new IllegalArgumentException("真实渠道已开启但凭据缺失或过短");
      }
      if (model == null || model.isBlank() || model.length() > 64) {
        throw new IllegalArgumentException("真实渠道已开启但模型标识缺失或不合法");
      }
    }
  }

  /** 只有同时具备地址、凭据、模型与正数调用上限时才允许真实提交。 */
  public boolean isReady() {
    return enabled && realCallLimit > 0 && !baseUrl.isBlank()
        && apiKey != null && apiKey.length() >= 8 && model != null && !model.isBlank();
  }

  public boolean supportsCancel() {
    return cancelPath != null && !cancelPath.isBlank();
  }

  public String submitUri() {
    return baseUrl + submitPath;
  }

  public String taskUri(String providerTaskId) {
    return baseUrl + taskPath.replace("{taskId}", encode(providerTaskId));
  }

  public String cancelUri(String providerTaskId) {
    return baseUrl + cancelPath.replace("{taskId}", encode(providerTaskId));
  }

  private String encode(String value) {
    return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void requireBaseUrl() {
    URI uri = parse(baseUrl);
    if (uri == null) {
      throw new IllegalArgumentException("真实渠道地址必须是可信的 HTTP(S) 地址");
    }
    if ("http".equalsIgnoreCase(uri.getScheme())) {
      Set<String> profiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
      boolean testProfile = !profiles.isEmpty() && TEST_PROFILES.containsAll(profiles);
      if (!allowInsecureHttp || !testProfile) {
        throw new IllegalArgumentException("只有显式 local/test 配置允许真实渠道使用明文 HTTP");
      }
    }
  }

  private URI parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      URI uri = new URI(value);
      return ("http".equalsIgnoreCase(uri.getScheme())
          || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null && uri.getUserInfo() == null
          && (uri.getPath() == null || uri.getPath().isEmpty()) ? uri : null;
    } catch (URISyntaxException invalid) {
      return null;
    }
  }

  private void requirePath(String name, String value) {
    if (value == null || !value.startsWith("/") || value.contains("?")
        || value.contains("#") || value.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException("真实渠道参数 " + name + " 不合法");
    }
  }

  private void requireRange(String name, long value, long min, long max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException("真实渠道参数 " + name + " 超出允许范围");
    }
  }
}
