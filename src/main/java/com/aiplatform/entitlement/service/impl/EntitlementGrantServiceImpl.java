package com.aiplatform.entitlement.service.impl;

import com.aiplatform.entitlement.EntitlementItems;
import com.aiplatform.entitlement.EntitlementKeys;
import com.aiplatform.entitlement.EntitlementSourceException;
import com.aiplatform.entitlement.TrustedEntitlement;
import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.entitlement.entity.EntitlementLedger;
import com.aiplatform.entitlement.mapper.EntitlementGrantMapper;
import com.aiplatform.entitlement.mapper.EntitlementLedgerMapper;
import com.aiplatform.entitlement.service.IEntitlementGrantService;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.mapper.BusinessOutboxMapper;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 权益包、发放流水和消费去重共用调用方事务；余额只以数据库为准。 */
@Slf4j
@Service
public class EntitlementGrantServiceImpl implements IEntitlementGrantService {
  private static final String ACTIVE = "ACTIVE";
  private static final String EXPIRED = "EXPIRED";
  private static final String GRANT = "GRANT";
  private static final String EXPIRE = "EXPIRE";
  private final EntitlementGrantMapper grants;
  private final EntitlementLedgerMapper ledgers;
  private final BusinessOutboxMapper outbox;
  private final PurchaseOrderMapper orders;
  private final EntitlementSourceVerifier verifier;
  private final TransactionTemplate transaction;

  public EntitlementGrantServiceImpl(
      EntitlementGrantMapper grants,
      EntitlementLedgerMapper ledgers,
      BusinessOutboxMapper outbox,
      PurchaseOrderMapper orders,
      EntitlementSourceVerifier verifier,
      PlatformTransactionManager transactionManager) {
    this.grants = grants;
    this.ledgers = ledgers;
    this.outbox = outbox;
    this.orders = orders;
    this.verifier = verifier;
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void registerForOrder(PurchaseOrder order) {
    if (order == null || order.getId() == null || order.getUserId() == null) {
      throw new IllegalArgumentException("订单缺少发放身份");
    }
    if (!OrderStatus.PAID.matches(order.getStatus())
        || order.getPaidAt() == null
        || (!FundingChannel.FREE.name().equals(order.getFunding())
          && !FundingChannel.MOCK.name().equals(order.getFunding()))) {
      throw new IllegalArgumentException("订单尚未形成可发放的购买完成事实");
    }
    outbox.insertEntitlementGrant(order.getId(), order.getUserId(),
        EntitlementKeys.grantTask(order.getId()));
  }

  @Override
  @Transactional
  public EntitlementGrant grantForTask(BusinessOutboxTask task) {
    if (task == null || task.getAggregateId() == null || task.getUserId() == null
        || !BusinessEventType.ENTITLEMENT_GRANT.name().equals(task.getKind())) {
      throw new EntitlementSourceException("TASK_INVALID", "发放任务身份不完整");
    }
    PurchaseOrder order = orders.selectOrderForUpdate(task.getAggregateId());
    if (order == null || !Objects.equals(task.getUserId(), order.getUserId())) {
      throw new EntitlementSourceException("ORDER_IDENTITY", "订单与发放任务身份不一致");
    }
    TrustedEntitlement source = verifier.verify(order);
    EntitlementGrant existing = grants.selectBySource(source.orderId(), EntitlementItems.CREDITS);
    if (existing != null) {
      verifyExisting(existing, source);
      return existing;
    }
    LocalDateTime now = grants.selectDatabaseNow();
    EntitlementGrant grant = newGrant(source);
    grants.insert(grant);
    insertGrantLedger(grant);
    if (!source.expiresAt().isAfter(now)) {
      // 迟到或补发时保留原始承诺，剩余可用次数直接转为已过期，不延长有效期。
      expireImmediately(grant);
    }
    return grants.selectById(grant.getId());
  }

  @Override
  public int expireDueGrants(int batch) {
    int limit = Math.max(1, Math.min(batch, 500));
    List<Long> ids = grants.selectDueIds(limit);
    int changed = 0;
    for (Long id : ids) {
      try {
        Boolean result = transaction.execute(status -> expireOne(id));
        if (Boolean.TRUE.equals(result)) {
          changed++;
        }
      } catch (RuntimeException failure) {
        // 单包异常不阻塞同批其他权益；下一轮扫描仍会看到同一到期包。
        log.warn("权益包到期处理失败，grantId={}，type={}",
            id, failure.getClass().getSimpleName());
      }
    }
    return changed;
  }

  private EntitlementGrant newGrant(TrustedEntitlement source) {
    EntitlementGrant grant = new EntitlementGrant();
    grant.setUserId(source.userId());
    grant.setOrderId(source.orderId());
    grant.setItemKey(EntitlementItems.CREDITS);
    grant.setProductId(source.productId());
    grant.setApplicationId(source.applicationId());
    grant.setApplicationName(source.applicationName());
    grant.setApplicationVersion(source.applicationVersion());
    grant.setModelVersion(source.modelVersion());
    grant.setImageSize(source.imageSize());
    grant.setOutputCount(source.outputCount());
    grant.setCredits(source.credits());
    grant.setValidDays(source.validDays());
    grant.setSourceFunding(source.funding());
    grant.setSourcePaidAt(source.paidAt());
    grant.setTotalCredits(source.credits());
    grant.setAvailableCredits(source.credits());
    grant.setFrozenCredits(0);
    grant.setConsumedCredits(0);
    grant.setExpiredCredits(0);
    grant.setEffectiveFrom(source.paidAt());
    grant.setExpiresAt(source.expiresAt());
    grant.setStatus(ACTIVE);
    return grant;
  }

  private void verifyExisting(EntitlementGrant grant, TrustedEntitlement source) {
    // 重复投递必须与首次发放依据完全一致；快照被改写时不能静默沿用旧权益包。
    boolean matched = Objects.equals(grant.getItemKey(), EntitlementItems.CREDITS)
        && Objects.equals(grant.getUserId(), source.userId())
        && Objects.equals(grant.getCredits(), source.credits())
        && Objects.equals(grant.getValidDays(), source.validDays())
        && Objects.equals(grant.getSourceFunding(), source.funding())
        && Objects.equals(grant.getSourcePaidAt(), source.paidAt())
        && Objects.equals(grant.getEffectiveFrom(), source.paidAt())
        && Objects.equals(grant.getExpiresAt(), source.expiresAt())
        && Objects.equals(grant.getTotalCredits(), source.credits())
        && Objects.equals(grant.getProductId(), source.productId())
        && Objects.equals(grant.getApplicationId(), source.applicationId())
        && Objects.equals(grant.getApplicationName(), source.applicationName())
        && Objects.equals(grant.getApplicationVersion(), source.applicationVersion())
        && Objects.equals(grant.getModelVersion(), source.modelVersion())
        && Objects.equals(grant.getImageSize(), source.imageSize())
        && Objects.equals(grant.getOutputCount(), source.outputCount());
    if (!matched) {
      throw new EntitlementSourceException("GRANT_MISMATCH",
          "同一来源订单已存在内容不同的权益包");
    }
  }

  private void insertGrantLedger(EntitlementGrant grant) {
    EntitlementLedger ledger = new EntitlementLedger();
    ledger.setGrantId(grant.getId());
    ledger.setUserId(grant.getUserId());
    ledger.setOrderId(grant.getOrderId());
    ledger.setItemKey(grant.getItemKey());
    ledger.setOperationKey(EntitlementKeys.grantOperation(grant.getOrderId()));
    ledger.setOperationType(GRANT);
    ledger.setDeltaTotal(grant.getTotalCredits());
    ledger.setDeltaAvailable(grant.getAvailableCredits());
    ledger.setDeltaFrozen(0);
    ledger.setDeltaConsumed(0);
    ledger.setDeltaExpired(0);
    ledger.setTotalAfter(grant.getTotalCredits());
    ledger.setAvailableAfter(grant.getAvailableCredits());
    ledger.setFrozenAfter(grant.getFrozenCredits());
    ledger.setConsumedAfter(grant.getConsumedCredits());
    ledger.setExpiredAfter(grant.getExpiredCredits());
    ledger.setReason("购买完成发放");
    ledgers.insert(ledger);
  }

  private void expireImmediately(EntitlementGrant grant) {
    int amount = grant.getAvailableCredits();
    if (amount <= 0) {
      grants.markExpired(grant.getId());
      grant.setStatus(EXPIRED);
      return;
    }
    if (grants.expireAvailable(grant.getId(), amount) != 1) {
      throw new IllegalStateException("权益包到期条件已变化");
    }
    grant.setAvailableCredits(0);
    grant.setExpiredCredits(grant.getExpiredCredits() + amount);
    grant.setStatus(EXPIRED);
    insertExpireLedger(grant, amount);
  }

  private Boolean expireOne(long id) {
    EntitlementGrant grant = grants.selectForUpdate(id);
    if (grant == null) {
      return false;
    }
    LocalDateTime now = grants.selectDatabaseNow();
    if (grant.getExpiresAt().isAfter(now)) {
      return false;
    }
    if (grant.getAvailableCredits() > 0) {
      int amount = grant.getAvailableCredits();
      if (grants.expireAvailable(id, amount) != 1) {
        throw new IllegalStateException("权益包到期条件已变化");
      }
      grant.setAvailableCredits(0);
      grant.setExpiredCredits(grant.getExpiredCredits() + amount);
      grant.setStatus(EXPIRED);
      insertExpireLedger(grant, amount);
      return true;
    }
    if (ACTIVE.equals(grant.getStatus())) {
      grants.markExpired(id);
      return true;
    }
    return false;
  }

  private void insertExpireLedger(EntitlementGrant grant, int amount) {
    EntitlementLedger ledger = new EntitlementLedger();
    ledger.setGrantId(grant.getId());
    ledger.setUserId(grant.getUserId());
    ledger.setOrderId(grant.getOrderId());
    ledger.setItemKey(grant.getItemKey());
    ledger.setOperationKey(EntitlementKeys.expireOperation(grant.getId(), grant.getExpiresAt()));
    ledger.setOperationType(EXPIRE);
    ledger.setDeltaTotal(0);
    ledger.setDeltaAvailable(-amount);
    ledger.setDeltaFrozen(0);
    ledger.setDeltaConsumed(0);
    ledger.setDeltaExpired(amount);
    ledger.setTotalAfter(grant.getTotalCredits());
    ledger.setAvailableAfter(grant.getAvailableCredits());
    ledger.setFrozenAfter(grant.getFrozenCredits());
    ledger.setConsumedAfter(grant.getConsumedCredits());
    ledger.setExpiredAfter(grant.getExpiredCredits());
    ledger.setReason("超过固定有效期，剩余可用次数转为已过期");
    ledgers.insert(ledger);
  }
}
