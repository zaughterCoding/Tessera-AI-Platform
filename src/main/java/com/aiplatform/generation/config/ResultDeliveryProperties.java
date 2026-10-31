package com.aiplatform.generation.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 结果拉取与保存策略；默认拒绝内网目标，只有显式测试配置才允许本机 HTTP 服务。
 * 允许名单必须同时开启开关并处于 local/test 环境，避免测试便利削弱生产策略。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.generation.result")
public class ResultDeliveryProperties {
  private boolean enabled = true;
  private int batch = 10;
  private int leaseSeconds = 60;
  private int maxSaveAttempts = 5;
  private int retryBaseSeconds = 2;
  private int retryMaxSeconds = 60;
  private int scanIntervalMs = 2000;
  private int settlementBatch = 50;
  private int settlementIntervalMs = 5000;
  private int connectTimeoutMs = 2000;
  private int readTimeoutMs = 5000;
  private int totalTimeoutMs = 15000;
  private long maxBytes = 10_000_000;
  private int maxRedirects = 3;
  private List<String> allowedHosts = new ArrayList<>();
  private boolean allowPrivateHosts;
  private String downloadSecret = "";
  private String simulatorResultBaseUrl = "";
  private int orphanGraceSeconds = 3600;
  private int orphanBatch = 100;
  private int orphanScanIntervalMs = 600000;
  private int cancelLeaseSeconds = 30;
  private int cancelBatch = 10;
  private int cancelScanIntervalMs = 2000;
  private int cancelMaxBackoffSeconds = 300;

  @Autowired
  private Environment environment;

  @PostConstruct
  public void validate() {
    requireRange("batch", batch, 1, 200);
    requireRange("lease-seconds", leaseSeconds, 5, 3600);
    requireRange("max-save-attempts", maxSaveAttempts, 1, 20);
    requireRange("retry-base-seconds", retryBaseSeconds, 1, 300);
    requireRange("retry-max-seconds", retryMaxSeconds, retryBaseSeconds, 3600);
    requireRange("scan-interval-ms", scanIntervalMs, 200, 60000);
    requireRange("settlement-batch", settlementBatch, 1, 1000);
    requireRange("settlement-interval-ms", settlementIntervalMs, 500, 60000);
    requireRange("connect-timeout-ms", connectTimeoutMs, 100, 30000);
    requireRange("read-timeout-ms", readTimeoutMs, 100, 60000);
    requireRange("total-timeout-ms", totalTimeoutMs, 500, 120000);
    requireRange("max-bytes", maxBytes, 1, 20_000_000);
    requireRange("max-redirects", maxRedirects, 0, 10);
    requireRange("orphan-grace-seconds", orphanGraceSeconds, 1, 604800);
    requireRange("orphan-batch", orphanBatch, 1, 1000);
    requireRange("orphan-scan-interval-ms", orphanScanIntervalMs, 1000, 86400000);
    requireRange("cancel-lease-seconds", cancelLeaseSeconds, 5, 600);
    requireRange("cancel-batch", cancelBatch, 1, 100);
    requireRange("cancel-scan-interval-ms", cancelScanIntervalMs, 200, 60000);
    requireRange("cancel-max-backoff-seconds", cancelMaxBackoffSeconds, 5, 3600);
    if (leaseSeconds * 1000L < totalTimeoutMs) {
      throw new IllegalArgumentException("结果认领租约必须覆盖拉取总超时");
    }
    if (allowPrivateHosts && !testProfile()) {
      throw new IllegalArgumentException("只有 local/test 环境允许结果拉取访问内网地址");
    }
    if (allowedHosts == null) {
      allowedHosts = new ArrayList<>();
    }
    List<String> normalized = new ArrayList<>();
    for (String host : allowedHosts) {
      if (host == null || host.isBlank()) {
        continue;
      }
      if (!host.matches("[A-Za-z0-9.:\\[\\]-]{1,255}")) {
        throw new IllegalArgumentException("结果拉取允许名单不合法");
      }
      normalized.add(host.toLowerCase(Locale.ROOT));
    }
    allowedHosts = normalized;
    if (!simulatorResultBaseUrl.isBlank()) {
      URI uri = parse(simulatorResultBaseUrl);
      if (uri == null) {
        throw new IllegalArgumentException("模拟结果地址必须是可信的 HTTP(S) 地址");
      }
      if (downloadSecret == null || downloadSecret.length() < 32) {
        throw new IllegalArgumentException("模拟结果下载密钥长度不足");
      }
    }
  }

  /**
   * 模拟渠道结果地址是显式配置的内部服务；只有该精确 host:port 可访问内网，
   * 其他内网目标仍需 test 环境开关，正式环境无法借此获得任意内网访问能力。
   */
  public boolean isSimulatorHost(String host, int port) {
    URI uri = parse(simulatorResultBaseUrl);
    if (uri == null || uri.getHost() == null) {
      return false;
    }
    int expectedPort = uri.getPort() < 0
        ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
    return uri.getHost().equalsIgnoreCase(host) && expectedPort == port;
  }

  private boolean testProfile() {
    for (String profile : environment.getActiveProfiles()) {
      if ("local".equals(profile) || "test".equals(profile)) {
        return true;
      }
    }
    return false;
  }

  private URI parse(String value) {
    try {
      URI uri = new URI(value);
      return ("http".equalsIgnoreCase(uri.getScheme())
          || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null && uri.getUserInfo() == null ? uri : null;
    } catch (URISyntaxException invalid) {
      return null;
    }
  }

  private void requireRange(String name, long value, long min, long max) {
    if (value < min || value > max) {
      throw new IllegalArgumentException("结果交付参数 " + name + " 超出允许范围");
    }
  }
}
