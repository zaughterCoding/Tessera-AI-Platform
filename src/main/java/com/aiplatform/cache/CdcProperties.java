package com.aiplatform.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** CDC 连接与重试参数；默认关闭，只有显式配置后才连接 Canal。 */
@Component
@ConfigurationProperties(prefix = "app.cdc")
@Getter
@Setter
public class CdcProperties {
  private boolean enabled;
  private String host = "127.0.0.1";
  private int port = 11111;
  private String destination = "ai_creation";
  private String schema;
  private String username = "";
  private String password = "";
  private int batchSize = 200;
  private long pollTimeoutMs = 1000;
  private long leaseSeconds = 30;
  private long reconnectMs = 1000;
}
