package com.aiplatform.entitlement.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.entitlement.EntitlementItems;
import com.aiplatform.entitlement.EntitlementKeys;
import com.aiplatform.entitlement.EntitlementSourceException;
import com.aiplatform.entitlement.TrustedEntitlement;
import com.aiplatform.entitlement.dto.EntitlementAnomalyDTO;
import com.aiplatform.entitlement.dto.EntitlementBackfillDTO;
import com.aiplatform.entitlement.dto.EntitlementBackfillForm;
import com.aiplatform.entitlement.dto.EntitlementReconciliationDTO;
import com.aiplatform.entitlement.entity.EntitlementAnomaly;
import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.entitlement.mapper.EntitlementAnomalyMapper;
import com.aiplatform.entitlement.mapper.EntitlementGrantMapper;
import com.aiplatform.entitlement.service.IEntitlementAdminService;
import com.aiplatform.entitlement.service.IEntitlementGrantService;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.trade.entity.PurchaseOrder;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 补发只登记稳定任务；实际发放、余额和流水仍由独立消费者与数据库约束决定。 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class EntitlementAdminServiceImpl implements IEntitlementAdminService {
  private static final int MAX_BACKFILL_BATCH = 200;
  private static final int MAX_ANOMALY_LIMIT = 200;
  private final EntitlementGrantMapper grants;
  private final EntitlementAnomalyMapper anomalies;
  private final EntitlementSourceVerifier verifier;
  private final BusinessOutboxMapper outbox;
  private final IEntitlementGrantService grantService;

  public EntitlementAdminServiceImpl(
      EntitlementGrantMapper grants,
      EntitlementAnomalyMapper anomalies,
      EntitlementSourceVerifier verifier,
      BusinessOutboxMapper outbox,
      IEntitlementGrantService grantService) {
    this.grants = grants;
    this.anomalies = anomalies;
    this.verifier = verifier;
    this.outbox = outbox;
    this.grantService = grantService;
  }

  @Override
  @Transactional
  public EntitlementBackfillDTO backfill(EntitlementBackfillForm form) {
    int limit = form == null || form.getLimit() == null ? 50 : form.getLimit();
    limit = Math.max(1, Math.min(limit, MAX_BACKFILL_BATCH));
    long after = form == null || form.getAfterOrderId() == null ? 0L : form.getAfterOrderId();
    List<PurchaseOrder> orders = grants.selectPaidOrdersAfter(after, limit);
    EntitlementBackfillDTO result = new EntitlementBackfillDTO();
    result.setAfterOrderId(after);
    result.setScanned(orders.size());
    long last = after;
    int trusted = 0;
    int registered = 0;
    int existingTasks = 0;
    int existingGrants = 0;
    int anomalyCount = 0;
    for (PurchaseOrder order : orders) {
      last = order.getId();
      EntitlementGrant grant = grants.selectBySource(order.getId(), EntitlementItems.CREDITS);
      if (grant != null) {
        existingGrants++;
        anomalies.resolve(order.getId(), EntitlementItems.CREDITS);
        continue;
      }
      if (grants.countTaskByOrder(order.getId()) > 0) {
        existingTasks++;
        continue;
      }
      try {
        TrustedEntitlement source = verifier.verify(order);
        trusted++;
        int inserted = outbox.insertEntitlementGrant(source.orderId(), source.userId(),
            EntitlementKeys.grantTask(source.orderId()));
        if (inserted == 1) {
          registered++;
        } else {
          existingTasks++;
        }
        anomalies.resolve(order.getId(), EntitlementItems.CREDITS);
      } catch (EntitlementSourceException failure) {
        anomalyCount++;
        anomalies.upsert(order.getId(), order.getUserId(), EntitlementItems.CREDITS,
            failure.getAnomalyType(), failure.getMessage());
      }
    }
    // 保留可空游标，避免与原始 long 混合导致空值拆箱；空页时沿用请求游标。
    Long next;
    if (orders.isEmpty()) {
      next = form == null ? null : form.getAfterOrderId();
    } else {
      next = last;
    }
    result.setNextAfterOrderId(next);
    result.setTrusted(trusted);
    result.setRegistered(registered);
    result.setExistingTasks(existingTasks);
    result.setExistingGrants(existingGrants);
    result.setAnomalies(anomalyCount);
    result.setHasMore(orders.size() == limit);
    result.setRemainingOrders(grants.countPaidOrdersAfter(next == null ? 0L : next));
    return result;
  }

  @Override
  public EntitlementReconciliationDTO reconcile() {
    EntitlementReconciliationDTO view = new EntitlementReconciliationDTO();
    view.setEligibleOrderItems(grants.countEligibleOrderItems());
    view.setRegisteredTasks(grants.countRegisteredTasks());
    view.setIssuedGrants(grants.countIssuedGrants());
    view.setPendingTasks(grants.countPendingTasks());
    view.setReviewTasks(grants.countReviewTasks());
    view.setFailedMessages(grants.countFailedMessages());
    view.setExpiredGrants(grants.countExpiredGrants());
    view.setMissingTasks(grants.countMissingTasks());
    view.setDoneWithoutGrant(grants.countDoneWithoutGrant());
    view.setDuplicateGrants(grants.countDuplicateGrants());
    view.setBalanceViolations(grants.countBalanceViolations());
    view.setLedgerMismatches(grants.countLedgerMismatches());
    view.setOpenAnomalies(anomalies.countOpen());
    boolean exact = view.getMissingTasks() == 0
        && view.getDoneWithoutGrant() == 0
        && view.getPendingTasks() == 0
        && view.getReviewTasks() == 0
        && view.getFailedMessages() == 0
        && view.getDuplicateGrants() == 0
        && view.getBalanceViolations() == 0
        && view.getLedgerMismatches() == 0
        && view.getOpenAnomalies() == 0;
    view.setExact(exact);
    view.setNote(exact ? "任务、权益包、余额与流水一致"
        : "存在待发放、失败、异常或账务不一致，需人工复核");
    return view;
  }

  @Override
  public int expire(int limit) {
    return grantService.expireDueGrants(Math.max(1, Math.min(limit, 500)));
  }

  @Override
  public List<EntitlementAnomalyDTO> anomalies(String status, int limit) {
    String normalized = status == null || status.isEmpty() ? null : status.trim().toUpperCase();
    if (normalized != null && !"OPEN".equals(normalized) && !"RESOLVED".equals(normalized)) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    int size = Math.max(1, Math.min(limit, MAX_ANOMALY_LIMIT));
    List<EntitlementAnomalyDTO> views = new ArrayList<>();
    for (EntitlementAnomaly anomaly : anomalies.selectList(normalized, size)) {
      views.add(EntitlementAnomalyDTO.from(anomaly));
    }
    return views;
  }
}
