package com.aiplatform.operations.service.impl;

import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.mapper.AnalyticsFailureMapper;
import com.aiplatform.media.MediaConfig;
import com.aiplatform.media.mapper.MediaAssetMapper;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.messaging.mapper.MessageMapper;
import com.aiplatform.operations.dto.MediaRetentionDTO;
import com.aiplatform.operations.dto.RetentionOverviewDTO;
import com.aiplatform.operations.dto.RetentionRunDTO;
import com.aiplatform.operations.dto.RetentionStatusDTO;
import com.aiplatform.operations.mapper.OperationAuditMapper;
import com.aiplatform.operations.retention.RetentionProperties;
import com.aiplatform.operations.retention.RetentionTarget;
import com.aiplatform.operations.service.IRetentionService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 有界、可预览、可重复执行的保留清理；不触碰在途、复核与去重仍在用的记录。 */
@Service
public class RetentionServiceImpl implements IRetentionService {
  private final RetentionProperties properties;
  private final AnalyticsProperties analytics;
  private final BusinessOutboxMapper outbox;
  private final MessageMapper messages;
  private final AnalyticsFailureMapper analyticsFailures;
  private final OperationAuditMapper audits;
  private final MediaAssetMapper media;
  private final MediaConfig mediaConfig;
  private final TransactionTemplate transaction;

  public RetentionServiceImpl(
      RetentionProperties properties,
      AnalyticsProperties analytics,
      BusinessOutboxMapper outbox,
      MessageMapper messages,
      AnalyticsFailureMapper analyticsFailures,
      OperationAuditMapper audits,
      MediaAssetMapper media,
      MediaConfig mediaConfig,
      PlatformTransactionManager transactionManager) {
    this.properties = properties;
    this.analytics = analytics;
    this.outbox = outbox;
    this.messages = messages;
    this.analyticsFailures = analyticsFailures;
    this.audits = audits;
    this.media = media;
    this.mediaConfig = mediaConfig;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Override
  public RetentionOverviewDTO overview() {
    RetentionOverviewDTO view = new RetentionOverviewDTO();
    for (RetentionTarget target : RetentionTarget.values()) {
      view.getDatabase().add(status(target));
    }
    MediaRetentionDTO mediaView = new MediaRetentionDTO();
    mediaView.setPendingGraceSeconds(mediaConfig.pendingGrace().getSeconds());
    mediaView.setOrphanGraceSeconds(mediaConfig.orphanGrace().getSeconds());
    mediaView.setPending(media.countByState("PENDING"));
    mediaView.setActive(media.countByState("ACTIVE"));
    mediaView.setDeleting(media.countByState("DELETING"));
    mediaView.setDeleted(media.countByState("DELETED"));
    view.setMedia(mediaView);
    view.setAnalyticsRetentionDays(analytics.getRetentionDays());
    view.setAnalyticsBackfillDays(analytics.getBackfillDays());
    view.setAnalyticsNote("分析明细与分钟桶由分析维护任务按保留期截断；"
        + "失败隔离与补数任务不进入自动清理");
    return view;
  }

  @Override
  public RetentionRunDTO run(RetentionTarget target, int maxBatches) {
    LocalDateTime cutoff = cutoff(target);
    int batches = Math.max(1, Math.min(maxBatches, 100));
    RetentionRunDTO result = new RetentionRunDTO();
    result.setTarget(target.key());
    result.setCutoff(cutoff);
    long deleted = 0;
    int executed = 0;
    while (executed < batches) {
      final RetentionTarget current = target;
      final LocalDateTime boundary = cutoff;
      Integer removed = transaction.execute(status ->
          delete(current, boundary, properties.getBatchSize()));
      int count = removed == null ? 0 : removed;
      deleted += count;
      executed++;
      if (count < properties.getBatchSize()) {
        break;
      }
    }
    result.setBatches(executed);
    result.setDeleted(deleted);
    result.setRemaining(count(target, cutoff));
    return result;
  }

  private RetentionStatusDTO status(RetentionTarget target) {
    LocalDateTime cutoff = cutoff(target);
    RetentionStatusDTO row = new RetentionStatusDTO();
    row.setTarget(target.key());
    row.setLabel(target.label());
    row.setRule(target.rule());
    row.setRetentionDays(days(target));
    row.setCutoff(cutoff);
    row.setEligible(count(target, cutoff));
    row.setBatchSize(properties.getBatchSize());
    return row;
  }

  private int delete(RetentionTarget target, LocalDateTime cutoff, int limit) {
    switch (target) {
      case BUSINESS_OUTBOX_DONE:
        return outbox.deleteDoneBefore(cutoff, limit);
      case MESSAGE_INBOX:
        return messages.deleteInboxBefore(cutoff, limit);
      case MESSAGE_FAILURE:
        return messages.deleteFailuresBefore(cutoff, limit);
      case ANALYTICS_FAILURE:
        return analyticsFailures.deleteFailuresBefore(cutoff, limit);
      case OPERATION_AUDIT:
        return audits.deleteBefore(cutoff, limit);
      default:
        throw new IllegalArgumentException("未知的保留清理目标");
    }
  }

  private long count(RetentionTarget target, LocalDateTime cutoff) {
    switch (target) {
      case BUSINESS_OUTBOX_DONE:
        return outbox.countDoneBefore(cutoff);
      case MESSAGE_INBOX:
        return messages.countInboxBefore(cutoff);
      case MESSAGE_FAILURE:
        return messages.countFailuresBefore(cutoff);
      case ANALYTICS_FAILURE:
        return analyticsFailures.countFailuresBefore(cutoff);
      case OPERATION_AUDIT:
        return audits.countBefore(cutoff);
      default:
        throw new IllegalArgumentException("未知的保留清理目标");
    }
  }

  private LocalDateTime cutoff(RetentionTarget target) {
    return LocalDateTime.now(ZoneOffset.UTC).minusDays(days(target));
  }

  private int days(RetentionTarget target) {
    switch (target) {
      case BUSINESS_OUTBOX_DONE:
        return properties.getOutboxDoneDays();
      case MESSAGE_INBOX:
        return properties.getInboxDays();
      case MESSAGE_FAILURE:
        return properties.getMessageFailureDays();
      case ANALYTICS_FAILURE:
        return properties.getAnalyticsFailureDays();
      case OPERATION_AUDIT:
        return properties.getAuditDays();
      default:
        throw new IllegalArgumentException("未知的保留清理目标");
    }
  }
}
