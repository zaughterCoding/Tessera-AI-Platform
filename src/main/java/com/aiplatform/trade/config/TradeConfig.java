package com.aiplatform.trade.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** 交易参数在启动时校验；模拟支付不允许在非本地/测试配置中启用。 */
@Configuration
public class TradeConfig {
  public TradeConfig(
      @Value("${app.trade.order-timeout-minutes:15}") int timeoutMinutes,
      @Value("${app.trade.expiry-scan-batch:50}") int scanBatch,
      @Value("${app.trade.mock-payment-enabled:false}") boolean mockEnabled,
      Environment environment) {
    if (timeoutMinutes < 1 || timeoutMinutes > 720) {
      throw new IllegalStateException("订单超时必须在 1～720 分钟之间");
    }
    if (scanBatch < 1 || scanBatch > 500) {
      throw new IllegalStateException("到期扫描批量必须在 1～500 之间");
    }
    if (mockEnabled) {
      Set<String> profiles = new HashSet<>(Arrays.asList(environment.getActiveProfiles()));
      if (profiles.isEmpty()
          || !new HashSet<>(Arrays.asList("local", "test")).containsAll(profiles)) {
        throw new IllegalStateException("模拟支付只允许在显式 local/test 配置下启用");
      }
    }
  }
}
