package com.aiplatform.campaign.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** 活动履约参数启动即校验；默认直接由 Stream 建单，Outbox 模式需显式开启。 */
@Configuration
public class CampaignConfig {
  public CampaignConfig(
      @Value("${app.campaign.fulfillment-mode:stream}") String mode,
      @Value("${app.campaign.consumer-batch:10}") int batch,
      @Value("${app.campaign.consumer-block-ms:1000}") int blockMillis,
      @Value("${app.campaign.claim-idle-ms:5000}") int claimIdleMillis,
      @Value("${app.campaign.max-deliveries:5}") int maxDeliveries,
      @Value("${app.campaign.request-ttl-seconds:2592000}") int requestTtlSeconds) {
    if (!"stream".equals(mode) && !"outbox".equals(mode)) {
      throw new IllegalStateException("活动履约模式只能是 stream 或 outbox");
    }
    if (batch < 1 || batch > 100) {
      throw new IllegalStateException("活动消费者批量必须在 1～100 之间");
    }
    if (blockMillis < 0 || blockMillis > 5000) {
      throw new IllegalStateException("活动消费者阻塞时间必须在 0～5000 毫秒之间");
    }
    if (claimIdleMillis < 0 || claimIdleMillis > 600000) {
      throw new IllegalStateException("Pending 接管空闲时间必须在 0～600000 毫秒之间");
    }
    if (maxDeliveries < 1 || maxDeliveries > 50) {
      throw new IllegalStateException("活动消息最大投递次数必须在 1～50 之间");
    }
    if (requestTtlSeconds < 60) {
      throw new IllegalStateException("预约状态最短保留 60 秒");
    }
  }
}
