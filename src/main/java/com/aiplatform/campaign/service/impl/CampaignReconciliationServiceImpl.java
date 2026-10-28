package com.aiplatform.campaign.service.impl;

import com.aiplatform.campaign.CampaignKeys;
import com.aiplatform.campaign.CampaignStreamSnapshot;
import com.aiplatform.campaign.dto.CampaignReconciliationDTO;
import com.aiplatform.campaign.dto.CampaignStreamMetricsDTO;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignReconciliationService;
import com.aiplatform.campaign.service.ICampaignRedisService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 数据库公式与 Redis 入口量分开展示；裁剪仅按消费组确认的安全水位推进。 */
@Slf4j
@Service
public class CampaignReconciliationServiceImpl implements ICampaignReconciliationService {
  private final LimitedCampaignMapper campaigns;
  private final CampaignReservationMapper reservations;
  private final ICampaignRedisService redis;
  private final BusinessOutboxMapper outbox;
  private final MeterRegistry metrics;
  private final CampaignKeys keys;

  public CampaignReconciliationServiceImpl(
      LimitedCampaignMapper campaigns,
      CampaignReservationMapper reservations,
      ICampaignRedisService redis,
      BusinessOutboxMapper outbox,
      MeterRegistry metrics,
      CampaignKeys keys) {
    this.campaigns = campaigns;
    this.reservations = reservations;
    this.redis = redis;
    this.outbox = outbox;
    this.metrics = metrics;
    this.keys = keys;
  }

  @Override
  public CampaignReconciliationDTO reconcile(long campaignId) {
    LimitedCampaign campaign = campaigns.selectById(campaignId);
    if (campaign == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    long committed = reservations.countUnreleasedSuccessWithOrder(campaignId);
    long processing = reservations.countByState(campaignId, ReservationState.PROCESSING.name());
    long review = reservations.countByState(campaignId, ReservationState.REVIEW.name());
    CampaignStreamSnapshot stream = redis.snapshot();
    Long redisStock = null;
    Integer redisGeneration = null;
    boolean redisReadable = true;
    try {
      redisStock = redis.redisStock(campaignId);
      redisGeneration = redis.redisGeneration(campaignId);
    } catch (RuntimeException failure) {
      // 状态损坏时不猜测库存，明确标注无法精确判定。
      redisReadable = false;
    }
    boolean formulaMatched =
        campaign.getInitialStock() == campaign.getRemainingStock() + committed;
    boolean generationMatched = Integer.valueOf(campaign.getGeneration()).equals(redisGeneration);
    Long difference = redisStock == null
        ? null : redisStock - campaign.getRemainingStock();
    long inFlight = redisStock == null
        ? processing + review
        : Math.max(0L, campaign.getRemainingStock() - redisStock);
    boolean exact = formulaMatched && redisReadable && redisStock != null
        && generationMatched && processing == 0 && review == 0
        && stream.getPending() == 0 && stream.getLag() == 0;

    CampaignReconciliationDTO view = new CampaignReconciliationDTO();
    view.setCampaignId(campaign.getId());
    view.setProductId(campaign.getProductId());
    view.setGeneration(campaign.getGeneration());
    view.setInitialStock(campaign.getInitialStock());
    view.setRemainingStock(campaign.getRemainingStock());
    view.setCommittedOrders(committed);
    view.setInFlightReservations(inFlight);
    view.setReviewReservations(review);
    view.setFormulaMatched(formulaMatched);
    view.setRedisStock(redisStock);
    view.setRedisGeneration(redisGeneration);
    view.setRedisDifference(difference);
    view.setPendingMessages(stream.getPending());
    view.setStreamLag(stream.getLag());
    view.setExact(exact);
    view.setNote(note(formulaMatched, redisReadable, redisStock, generationMatched,
        processing, review, stream));
    return view;
  }

  @Override
  public CampaignStreamMetricsDTO streamMetrics() {
    CampaignStreamSnapshot stream = redis.snapshot();
    long pendingHandoffs = outbox.countPendingCampaignHandoffs();
    long unresolved = reservations.countProcessingOrReview(null);
    long review = reservations.countAllByState(ReservationState.REVIEW.name());
    String watermark = stream.isGroupExists() ? stream.getLastDeliveredId() : null;
    List<String> blockers = new ArrayList<>();
    if (!stream.isStreamExists()) {
      blockers.add("待处理 Stream 尚未建立");
    } else if (!stream.isGroupExists()) {
      blockers.add("消费组尚未建立");
    }
    if (stream.getPending() > 0) {
      blockers.add("存在未确认 Pending 消息");
    }
    if (stream.getLag() > 0) {
      blockers.add("存在未投递消息");
    }
    if (unresolved > 0) {
      blockers.add("存在处理中或待复核预约");
    }
    if (pendingHandoffs > 0) {
      blockers.add("存在未完成的建单或重试交接");
    }
    if (watermark == null || "0-0".equals(watermark)) {
      blockers.add("尚无可用的裁剪水位");
    }
    CampaignStreamMetricsDTO view = new CampaignStreamMetricsDTO();
    view.setStream(keys.stream());
    view.setGroup(keys.group());
    view.setStreamLength(stream.getLength());
    view.setPendingCount(stream.getPending());
    view.setStreamLag(stream.getLag());
    view.setOldestPendingAgeMillis(stream.getOldestPendingAgeMillis());
    view.setTrimWatermark(watermark);
    view.setSafeToTrim(blockers.isEmpty());
    view.setBlockedReason(blockers.isEmpty() ? null : String.join("；", blockers));
    view.setPendingHandoffs(pendingHandoffs);
    view.setReviewReservations(review);
    return view;
  }

  @Override
  public boolean trimIfSafe() {
    CampaignStreamMetricsDTO view = streamMetrics();
    if (!view.isSafeToTrim()) {
      return false;
    }
    long removed = redis.trimBelow(after(view.getTrimWatermark()));
    metrics.counter("aiplatform.campaign.stream.trimmed").increment(removed);
    if (removed > 0) {
      log.info("活动 Stream 已按安全水位裁剪，removed={}，watermark={}",
          removed, view.getTrimWatermark());
    }
    return true;
  }

  /** MINID 只删除小于水位的内容，因此推进一个序号才能裁掉最后一条已确认消息。 */
  private String after(String id) {
    int dash = id.indexOf('-');
    if (dash <= 0) {
      return id;
    }
    long millis;
    long sequence;
    try {
      millis = Long.parseLong(id.substring(0, dash));
      sequence = Long.parseLong(id.substring(dash + 1));
    } catch (NumberFormatException failure) {
      return id;
    }
    if (sequence == Long.MAX_VALUE) {
      return (millis + 1) + "-0";
    }
    return millis + "-" + (sequence + 1);
  }

  private String note(boolean formulaMatched, boolean redisReadable, Long redisStock,
      boolean generationMatched, long processing, long review, CampaignStreamSnapshot stream) {
    List<String> notes = new ArrayList<>();
    if (!formulaMatched) {
      notes.add("数据库库存公式不成立，需人工核对，禁止自动修正");
    }
    if (!redisReadable || redisStock == null) {
      notes.add("Redis 库存状态缺失或不可信，入口应保持关闭");
    } else if (!generationMatched) {
      notes.add("Redis 代次与数据库不一致，差异仅作展示");
    } else if (redisStock != null) {
      notes.add("Redis 差异按在途预约单列，不自动覆盖数据库库存");
    }
    if (processing > 0) {
      notes.add("存在处理中预约，最终结果尚未确定");
    }
    if (review > 0) {
      notes.add("存在待复核预约，需人工处理");
    }
    if (stream.getPending() > 0 || stream.getLag() > 0) {
      notes.add("Stream 仍有未确认或未投递消息");
    }
    if (notes.isEmpty()) {
      notes.add("库存、预约与消息水位一致");
    }
    return String.join("；", notes);
  }
}
