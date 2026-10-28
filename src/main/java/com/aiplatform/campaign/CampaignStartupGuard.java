package com.aiplatform.campaign;

import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignRedisService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时只核对入口状态；Redis 缺失或代次不符时保持关闭并留下指标，不自动重建。 */
@Slf4j
@Component
public class CampaignStartupGuard implements ApplicationRunner {
  private final LimitedCampaignMapper campaigns;
  private final ICampaignRedisService redis;
  private final MeterRegistry metrics;

  public CampaignStartupGuard(
      LimitedCampaignMapper campaigns,
      ICampaignRedisService redis,
      MeterRegistry metrics) {
    this.campaigns = campaigns;
    this.redis = redis;
    this.metrics = metrics;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    List<LimitedCampaign> published = campaigns.selectList(
        new LambdaQueryWrapper<LimitedCampaign>()
            .isNotNull(LimitedCampaign::getPublishedAt));
    for (LimitedCampaign campaign : published) {
      try {
        String state = redis.activityState(campaign.getId());
        Long stock = redis.redisStock(campaign.getId());
        Integer generation = redis.redisGeneration(campaign.getId());
        boolean aligned = state != null && stock != null
            && campaign.getGeneration().equals(generation)
            && campaign.getPublishState().equals(state);
        if (!aligned) {
          metrics.counter("aiplatform.campaign.entry.guard", "result", "closed").increment();
          log.error("活动入口状态与数据库不一致，已保持关闭，campaignId={}，dbState={}，"
                  + "redisState={}，dbGeneration={}，redisGeneration={}，redisStock={}",
              campaign.getId(), campaign.getPublishState(), state,
              campaign.getGeneration(), generation, stock);
        }
      } catch (RuntimeException failure) {
        metrics.counter("aiplatform.campaign.entry.guard", "result", "unavailable").increment();
        log.error("活动入口状态核对失败，已保持关闭，campaignId={}，type={}",
            campaign.getId(), failure.getClass().getSimpleName());
      }
    }
  }
}
