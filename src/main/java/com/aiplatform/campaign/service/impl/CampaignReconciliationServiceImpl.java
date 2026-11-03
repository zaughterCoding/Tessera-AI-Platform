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
    long pendingEvents = outbox.countPendingCampaignEvents(campaignId);
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
    String differenceKind = differenceKind(
        formulaMatched, redisReadable, redisStock, generationMatched,
        difference, processing, review, pendingEvents);
    // 只有公式、代次、库存差值与全部消费组水位都确认一致，且没有未完成事件时才标记精确。
    boolean exact = "CONFIRMED".equals(differenceKind)
        && processing == 0 && review == 0 && pendingEvents == 0
        && stream.isGroupExists() && stream.isAllGroupsCaughtUp();

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
    view.setStockDifferenceKind(differenceKind);
    view.setPendingMessages(stream.getTotalPending());
    view.setStreamLag(stream.getMaxGroupLag());
    view.setUnknownLagGroups(stream.getUnknownLagGroups());
    view.setPendingEvents(pendingEvents);
    view.setExact(exact);
    view.setNote(note(differenceKind, formulaMatched, redisReadable, redisStock,
        generationMatched, processing, review, pendingEvents, stream));
    return view;
  }

  /** 确认一致、可解释在途与无法解释差异分开表达，任何差异都不自动修正。 */
  private String differenceKind(boolean formulaMatched, boolean redisReadable, Long redisStock,
      boolean generationMatched, Long difference, long processing, long review,
      long pendingEvents) {
    if (!formulaMatched || !redisReadable || redisStock == null
        || !generationMatched || difference == null) {
      return "UNKNOWN";
    }
    if (difference == 0) {
      return "CONFIRMED";
    }
    // 库存归还事件未处理完时 Redis 会暂时低于数据库，这同样是在途差异，不需要人工介入。
    if (difference < 0 && -difference <= processing + review + pendingEvents) {
      return "IN_FLIGHT";
    }
    return "UNEXPLAINED";
  }

  @Override
  public CampaignStreamMetricsDTO streamMetrics() {
    CampaignStreamSnapshot stream = redis.snapshot();
    long pendingHandoffs = outbox.countPendingCampaignHandoffs();
    long unresolved = reservations.countProcessingOrReview(null);
    long review = reservations.countAllByState(ReservationState.REVIEW.name());
    String watermark = stream.getTrimWatermark();
    List<String> blockers = new ArrayList<>();
    if (!stream.isStreamExists()) {
      blockers.add("待处理 Stream 尚未建立");
    } else if (stream.getGroupCount() == 0 || !stream.isGroupExists()) {
      blockers.add("消费组尚未建立");
    }
    if (stream.getTotalPending() > 0) {
      blockers.add(stream.getOtherPending() > 0
          ? "存在未确认 Pending 消息（含其他消费组）" : "存在未确认 Pending 消息");
    }
    if (stream.getUnknownLagGroups() > 0) {
      blockers.add("存在 lag 无法确定的消费组");
    }
    if (stream.getMaxGroupLag() > 0) {
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
    view.setPendingCount(stream.getTotalPending());
    view.setStreamLag(stream.getMaxGroupLag());
    view.setOldestPendingAgeMillis(stream.getOldestPendingAgeMillis());
    view.setTrimWatermark(watermark);
    view.setSafeToTrim(blockers.isEmpty());
    view.setBlockedReason(blockers.isEmpty() ? null : String.join("；", blockers));
    view.setPendingHandoffs(pendingHandoffs);
    view.setReviewReservations(review);
    view.setConsumerGroups(stream.getGroupCount());
    view.setOtherPendingCount(stream.getOtherPending());
    view.setUnknownLagGroups(stream.getUnknownLagGroups());
    return view;
  }

  @Override
  public boolean trimIfSafe() {
    CampaignStreamMetricsDTO view = streamMetrics();
    if (!view.isSafeToTrim()) {
      return false;
    }
    // 展示判断与执行之间可能新建消费组或新增投递，脚本内重新核对全部消费组。
    long removed = redis.trimIfAllSafe();
    if (removed < 0) {
      metrics.counter("aiplatform.campaign.stream.trim.skipped").increment();
      return false;
    }
    metrics.counter("aiplatform.campaign.stream.trimmed").increment(removed);
    if (removed > 0) {
      log.info("活动 Stream 已按安全水位裁剪，removed={}，watermark={}",
          removed, view.getTrimWatermark());
    }
    return true;
  }

  private String note(String differenceKind, boolean formulaMatched, boolean redisReadable,
      Long redisStock, boolean generationMatched, long processing, long review,
      long pendingEvents, CampaignStreamSnapshot stream) {
    List<String> notes = new ArrayList<>();
    if (!formulaMatched) {
      notes.add("数据库库存公式不成立，需人工核对，禁止自动修正");
    }
    if (!redisReadable || redisStock == null) {
      notes.add("Redis 库存状态缺失或不可信，入口应保持关闭");
    } else if (!generationMatched) {
      notes.add("Redis 代次与数据库不一致，不能判定精确");
    }
    if ("IN_FLIGHT".equals(differenceKind)) {
      notes.add("Redis 库存低于数据库的差异可由在途预约或未完成事件解释，最终结果尚未确定");
    } else if ("UNEXPLAINED".equals(differenceKind)) {
      notes.add("库存差异无法由公式、在途预约或未完成事件解释，需人工核对，禁止自动修正");
    }
    if (processing > 0) {
      notes.add("存在处理中预约，最终结果尚未确定");
    }
    if (review > 0) {
      notes.add("存在待复核预约，需人工处理");
    }
    if (pendingEvents > 0) {
      notes.add("存在未完成的建单、重试或库存释放事件，最终库存尚未确定");
    }
    if (stream.getUnknownLagGroups() > 0) {
      notes.add("存在 lag 无法确定的消费组");
    }
    if (stream.getTotalPending() > 0 || stream.getMaxGroupLag() > 0) {
      notes.add("Stream 仍有未确认或未投递消息");
    }
    if (notes.isEmpty()) {
      notes.add("库存、预约与消息水位一致");
    }
    return String.join("；", notes);
  }
}
