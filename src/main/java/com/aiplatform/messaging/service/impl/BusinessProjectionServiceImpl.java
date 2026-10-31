package com.aiplatform.messaging.service.impl;

import com.aiplatform.analytics.service.IAnalyticsJournalService;
import com.aiplatform.cache.CacheClient;
import com.aiplatform.cache.CacheUnavailableException;
import com.aiplatform.campaign.CampaignMessage;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.entity.LimitedCampaign;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.mapper.LimitedCampaignMapper;
import com.aiplatform.campaign.service.ICampaignFulfillmentService;
import com.aiplatform.campaign.service.ICampaignRedisService;
import com.aiplatform.catalog.mapper.UsageProductMapper;
import com.aiplatform.community.service.IFeedService;
import com.aiplatform.entitlement.service.IEntitlementGrantService;
import com.aiplatform.media.service.IMediaService;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.ProjectionResult;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.messaging.service.IBusinessProjectionService;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import com.aiplatform.trade.service.IOrderLifecycleService;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 只处理当前阶段真实注册的事件；缓存内容可重建，数据库任务才是事实。 */
@Service
public class BusinessProjectionServiceImpl implements IBusinessProjectionService {
  private final CacheClient cache;
  private final UsageProductMapper products;
  private final PurchaseOrderMapper orders;
  private final IOrderLifecycleService lifecycle;
  private final CampaignReservationMapper reservations;
  private final LimitedCampaignMapper campaigns;
  private final ICampaignFulfillmentService fulfillment;
  private final ICampaignRedisService campaignRedis;
  private final IMediaService media;
  private final IFeedService feed;
  private final BusinessOutboxMapper outbox;
  private final IAnalyticsJournalService analytics;
  private final IEntitlementGrantService entitlements;
  private final String prefix;
  private final boolean outboxMode;

  public BusinessProjectionServiceImpl(
      CacheClient cache,
      UsageProductMapper products,
      PurchaseOrderMapper orders,
      IOrderLifecycleService lifecycle,
      CampaignReservationMapper reservations,
      LimitedCampaignMapper campaigns,
      ICampaignFulfillmentService fulfillment,
      ICampaignRedisService campaignRedis,
      IMediaService media,
      IFeedService feed,
      BusinessOutboxMapper outbox,
      IAnalyticsJournalService analytics,
      IEntitlementGrantService entitlements,
      @Value("${app.redis.key-prefix}") String prefix,
      @Value("${app.campaign.fulfillment-mode:stream}") String campaignMode) {
    this.cache = cache;
    this.products = products;
    this.orders = orders;
    this.lifecycle = lifecycle;
    this.reservations = reservations;
    this.campaigns = campaigns;
    this.fulfillment = fulfillment;
    this.campaignRedis = campaignRedis;
    this.media = media;
    this.feed = feed;
    this.outbox = outbox;
    this.analytics = analytics;
    this.entitlements = entitlements;
    this.prefix = prefix;
    this.outboxMode = "outbox".equals(campaignMode);
  }

  @Override
  public ProjectionResult project(BusinessOutboxTask task) {
    BusinessEventType type = BusinessEventType.from(task.getKind());
    switch (type) {
      case APPLICATION_INVALIDATE:
        invalidateApplication(task.getAggregateId());
        return ProjectionResult.complete();
      case PRODUCT_INVALIDATE:
        invalidateProduct(task);
        return ProjectionResult.complete();
      case ORDER_CREATED:
        projectOrder(task, null, null);
        return ProjectionResult.complete();
      case ORDER_PAID:
        projectOrder(task, OrderStatus.PAID.name(), FundingChannel.MOCK.name());
        return ProjectionResult.complete();
      case ORDER_CLAIMED:
        projectOrder(task, OrderStatus.PAID.name(), FundingChannel.FREE.name());
        return ProjectionResult.complete();
      case ORDER_CLOSED:
        projectOrder(task, OrderStatus.CANCELLED.name(), null);
        return ProjectionResult.complete();
      case ORDER_EXPIRE:
        closeExpiredOrder(task);
        return ProjectionResult.complete();
      case CAMPAIGN_FULFILL:
        projectCampaignFulfillment(task);
        return ProjectionResult.complete();
      case CAMPAIGN_STOCK_RELEASED:
        projectCampaignStockRelease(task);
        return ProjectionResult.complete();
      case CAMPAIGN_RETRY:
        projectCampaignRetry(task);
        return ProjectionResult.complete();
      case MEDIA_DELETE:
        projectMediaDelete(task);
        return ProjectionResult.complete();
      case CREATION_PUBLISH:
        return projectCreationPublish(task);
      case ENTITLEMENT_GRANT:
        entitlements.grantForTask(task);
        return ProjectionResult.complete();
      default:
        throw new IllegalArgumentException("没有可用的投影处理器");
    }
  }

  private void projectMediaDelete(BusinessOutboxTask task) {
    if (task.getUserId() == null) {
      throw new IllegalArgumentException("媒体删除任务缺少用户身份");
    }
    media.completeDeletion(task.getUserId(), task.getAggregateId());
  }

  private ProjectionResult projectCreationPublish(BusinessOutboxTask task) {
    if (task.getUserId() == null) {
      throw new IllegalArgumentException("发布任务缺少作者身份");
    }
    long cursor = task.getCursorId() == null ? 0L : task.getCursorId();
    return feed.fanout(task.getAggregateId(), task.getUserId(), cursor);
  }

  private void invalidateApplication(long applicationId) {
    String base = prefix + "catalog:application:";
    boolean detail = cache.invalidate(base + applicationId);
    boolean products = cache.invalidate(prefix + "catalog:product:list:" + applicationId);
    int search = cache.invalidatePrefix(prefix + "catalog:application:search:");
    if (!detail || !products || search < 0) {
      throw new CacheUnavailableException("目录缓存失效未全部完成，等待任务重试");
    }
  }

  private void invalidateProduct(BusinessOutboxTask task) {
    long productId = task.getAggregateId();
    Long applicationId = products.selectApplicationId(productId);
    if (applicationId == null) {
      throw new IllegalArgumentException("商品不存在，无法完成缓存失效");
    }
    boolean detail = cache.invalidate(prefix + "catalog:product:" + productId);
    boolean list = cache.invalidate(prefix + "catalog:product:list:" + applicationId);
    if (!detail || !list) {
      throw new CacheUnavailableException("商品缓存失效未全部完成，等待任务重试");
    }
  }

  private void closeExpiredOrder(BusinessOutboxTask task) {
    PurchaseOrder order = requireOrder(task);
    // 事件只定位订单；是否到期和能否关闭由数据库行锁与条件更新决定。
    lifecycle.closeExpired(order.getId());
  }

  private void projectOrder(BusinessOutboxTask task, String expectedStatus,
      String expectedFunding) {
    PurchaseOrder order = requireOrder(task);
    if (expectedStatus != null && !expectedStatus.equals(order.getStatus())) {
      throw new IllegalArgumentException("订单状态与事件不一致");
    }
    if (expectedFunding != null && !expectedFunding.equals(order.getFunding())) {
      throw new IllegalArgumentException("订单资金来源与事件不一致");
    }
    // 投影按事件身份幂等，重复投递不会重复产生历史效果。
    orders.insertOrderEvent(task.getId(), order.getId(), task.getKind(), task.getCreatedAt());
    // 分析任务与订单历史投影同事务登记；分析关闭时不产生任何记录。
    analytics.appendOrderFact(order, task);
    if (BusinessEventType.ORDER_PAID.name().equals(task.getKind())
        || BusinessEventType.ORDER_CLAIMED.name().equals(task.getKind())) {
      // 发放任务独立成组，和订单历史投影各自完成自己的 DONE 状态。
      entitlements.registerForOrder(order);
    }
  }

  private void projectCampaignFulfillment(BusinessOutboxTask task) {
    CampaignReservation reservation = requireReservation(task);
    if (ReservationState.SUCCESS.name().equals(reservation.getState())
        || ReservationState.FAILED.name().equals(reservation.getState())) {
      return;
    }
    if (!ReservationState.PROCESSING.name().equals(reservation.getState())) {
      throw new IllegalStateException("预约已隔离，等待人工复核");
    }
    fulfillment.fulfill(task.getAggregateId());
  }

  private void projectCampaignStockRelease(BusinessOutboxTask task) {
    CampaignReservation reservation = requireReservation(task);
    LimitedCampaign campaign = campaigns.selectById(reservation.getCampaignId());
    if (campaign == null) {
      throw new IllegalArgumentException("活动不存在，无法释放库存");
    }
    String target;
    if (ReservationState.FAILED.name().equals(reservation.getState())) {
      target = "FAILED";
    } else if (ReservationState.SUCCESS.name().equals(reservation.getState())) {
      if (!Boolean.TRUE.equals(reservation.getStockReleased())) {
        throw new IllegalStateException("库存释放标记尚未提交");
      }
      target = "RELEASED";
    } else {
      throw new IllegalStateException("预约状态不允许释放库存");
    }
    // 0 表示代次或归属已变化，属于安全空操作；负值需要重试或人工复核。
    long result = campaignRedis.release(campaign.getId(), reservation.getUserId(),
        reservation.getOrderId(), reservation.getGeneration(), target,
        reservation.getRequestKey(), campaign.getInitialStock());
    if (result < 0) {
      throw new IllegalStateException("活动库存释放未完成，返回值 " + result);
    }
  }

  private void projectCampaignRetry(BusinessOutboxTask task) {
    CampaignReservation reservation = requireReservation(task);
    if (ReservationState.SUCCESS.name().equals(reservation.getState())
        || ReservationState.FAILED.name().equals(reservation.getState())) {
      return;
    }
    if (!ReservationState.PROCESSING.name().equals(reservation.getState())) {
      throw new IllegalStateException("预约仍处于人工复核状态");
    }
    LimitedCampaign campaign = campaigns.selectById(reservation.getCampaignId());
    if (campaign == null || campaign.getPublishedAt() == null) {
      throw new IllegalArgumentException("活动不存在或尚未发布");
    }
    if (!Objects.equals(campaign.getGeneration(), reservation.getGeneration())) {
      fulfillment.markReview(reservation.getOrderId(), "重试时预约代次已过期");
      throw new IllegalStateException("预约代次已过期，等待人工复核");
    }
    CampaignMessage message = new CampaignMessage();
    message.setOrderId(reservation.getOrderId());
    message.setUserId(reservation.getUserId());
    message.setCampaignId(reservation.getCampaignId());
    message.setGeneration(reservation.getGeneration());
    message.setRequestKey(reservation.getRequestKey());
    if (outboxMode) {
      if (outbox.requeueCampaignFulfillment(reservation.getOrderId()) == 0) {
        outbox.insertCampaignFulfillment(reservation.getOrderId(), reservation.getUserId(),
            "campaign-fulfill:" + reservation.getOrderId());
      }
      return;
    }
    campaignRedis.enqueue(message);
  }

  private CampaignReservation requireReservation(BusinessOutboxTask task) {
    CampaignReservation reservation = reservations.selectReservation(task.getAggregateId());
    if (reservation == null
        || !Objects.equals(reservation.getUserId(), task.getUserId())) {
      throw new IllegalArgumentException("预约与任务身份不一致");
    }
    return reservation;
  }

  private PurchaseOrder requireOrder(BusinessOutboxTask task) {
    PurchaseOrder order = orders.selectById(task.getAggregateId());
    if (order == null || !Objects.equals(order.getUserId(), task.getUserId())) {
      throw new IllegalArgumentException("订单与任务身份不一致");
    }
    return order;
  }
}
