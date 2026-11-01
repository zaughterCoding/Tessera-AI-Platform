package com.aiplatform.generation.config;

import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import com.aiplatform.generation.provider.HttpImageProviderAdapter;
import com.aiplatform.generation.provider.ProviderCapabilities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时把公开的渠道限额写入渠道行，完成迁移后、Web 接受流量前生效。
 * 运行期限额只认数据库条件更新；同步只改上限，绝不重置排队、在途与用户活动计数。
 */
@Slf4j
@Component
@DependsOn("flywayInitializer")
public class GenerationChannelLimitInitializer implements InitializingBean {
  private final ProviderChannelMapper channels;
  private final GenerationProperties properties;
  private final HttpImageProviderAdapter http;
  private final JdbcTemplate jdbc;

  public GenerationChannelLimitInitializer(ProviderChannelMapper channels,
      GenerationProperties properties, HttpImageProviderAdapter http, JdbcTemplate jdbc) {
    this.channels = channels;
    this.properties = properties;
    this.http = http;
    this.jdbc = jdbc;
  }

  @Override
  public void afterPropertiesSet() {
    if (!channelTableExists()) {
      // V11 历史升级夹具只迁移到调度表之前，此时没有可同步的渠道事实。
      log.warn("provider_channel 尚不存在，跳过渠道限额同步");
      return;
    }
    applyConfiguredLimits();
  }

  /** 供启动与验收复用；只同步限额列，返回缺失渠道时立即失败。 */
  public void applyConfiguredLimits() {
    int updated = channels.syncLimits(properties.getChannelCode(),
        properties.getChannelMaxInFlight(), properties.getChannelQueueCapacity(),
        properties.getQueueTimeoutSeconds());
    if (updated != 1) {
      throw new IllegalStateException(
          "生成渠道不存在，无法应用限额配置: " + properties.getChannelCode());
    }
    log.info("生成渠道限额已同步，channel={}, maxInFlight={}, queueCapacity={}, "
            + "queueTimeoutSeconds={}", properties.getChannelCode(),
        properties.getChannelMaxInFlight(), properties.getChannelQueueCapacity(),
        properties.getQueueTimeoutSeconds());
    syncHttpDeclaration();
  }

  /** 真实渠道的启用状态与能力只由显式配置决定；未配置时保持停用，不产生任何外呼。 */
  private void syncHttpDeclaration() {
    ProviderChannel channel = channels.selectByCode(properties.getChannelCode());
    if (channel == null || !"HTTP".equals(channel.getKind())) {
      return;
    }
    ProviderCapabilities capabilities = http.capabilities();
    String status = http.isConfigured() ? "ENABLED" : "DISABLED";
    channels.syncChannelDeclaration(channel.getCode(), status,
        capabilities.idempotentSubmit(), capabilities.queryByAttempt(), capabilities.cancel());
    log.info("真实渠道声明已同步，channel={}, status={}, idempotentSubmit={}, "
            + "queryByAttempt={}, cancel={}", channel.getCode(), status,
        capabilities.idempotentSubmit(), capabilities.queryByAttempt(), capabilities.cancel());
  }

  private boolean channelTableExists() {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
        + "WHERE table_schema = DATABASE() AND table_name = 'provider_channel'", Integer.class);
    return count != null && count > 0;
  }
}
