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
  private final CampaignEntryGuard entryGuard;
  private final MeterRegistry metrics;

  public CampaignStartupGuard(
      LimitedCampaignMapper campaigns,
      ICampaignRedisService redis,
      CampaignEntryGuard entryGuard,
      MeterRegistry metrics) {
    this.campaigns = campaigns;
    this.redis = redis;
    this.entryGuard = entryGuard;
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
        // Redis 库存允许因在途预约低于数据库，但高于数据库剩余必然不可信。
        boolean aligned = state != null && stock != null && stock >= 0
            && stock <= campaign.getRemainingStock()
            && campaign.getGeneration().equals(generation)
            && campaign.getPublishState().equals(state);
        if (!aligned) {
          // 不仅记录日志，还要在受理入口实际拒绝请求，直到恢复重建成功。
          entryGuard.block(campaign.getId(), "启动核对发现 Redis 活动状态不一致");
          metrics.counter("aiplatform.campaign.entry.guard", "result", "closed").increment();
          log.error("活动入口状态与数据库不一致，已保持关闭，campaignId={}，dbState={}，"
                  + "redisState={}，dbGeneration={}，redisGeneration={}，redisStock={}",
              campaign.getId(), campaign.getPublishState(), state,
              campaign.getGeneration(), generation, stock);
        } else {
          entryGuard.unblock(campaign.getId());
        }
      } catch (RuntimeException failure) {
        entryGuard.block(campaign.getId(), "启动核对无法读取 Redis 活动状态");
        metrics.counter("aiplatform.campaign.entry.guard", "result", "unavailable").increment();
        log.error("活动入口状态核对失败，已保持关闭，campaignId={}，type={}",
            campaign.getId(), failure.getClass().getSimpleName());
      }
    }
  }
}
