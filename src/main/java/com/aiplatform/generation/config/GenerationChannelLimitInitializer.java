package com.aiplatform.generation.config;

import com.aiplatform.generation.mapper.ProviderChannelMapper;
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
  private final JdbcTemplate jdbc;

  public GenerationChannelLimitInitializer(ProviderChannelMapper channels,
      GenerationProperties properties, JdbcTemplate jdbc) {
    this.channels = channels;
    this.properties = properties;
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
  }

  private boolean channelTableExists() {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
        + "WHERE table_schema = DATABASE() AND table_name = 'provider_channel'", Integer.class);
    return count != null && count > 0;
  }
}
