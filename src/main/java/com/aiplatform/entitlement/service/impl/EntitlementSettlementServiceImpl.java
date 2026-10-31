package com.aiplatform.entitlement.service.impl;

import com.aiplatform.entitlement.EntitlementKeys;
import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.entitlement.entity.EntitlementLedger;
import com.aiplatform.entitlement.entity.GenerationSettlement;
import com.aiplatform.entitlement.mapper.EntitlementGrantMapper;
import com.aiplatform.entitlement.mapper.EntitlementLedgerMapper;
import com.aiplatform.entitlement.mapper.GenerationSettlementMapper;
import com.aiplatform.entitlement.service.IEntitlementSettlementService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 结算只写本模块余额与流水；任务状态和结果事实由调用方在同一事务内先行条件更新。 */
@Service
public class EntitlementSettlementServiceImpl implements IEntitlementSettlementService {
  private static final String ACTIVE = "ACTIVE";
  private static final String CONSUME = "CONSUME";
  private static final String RELEASE = "RELEASE";
  private static final String FREEZE = "FREEZE";
  private static final String AVAILABLE = "AVAILABLE";
  private static final String EXPIRED = "EXPIRED";
  private static final String TASK_REFERENCE = "TASK";

  private final EntitlementGrantMapper grants;
  private final EntitlementLedgerMapper ledgers;
  private final GenerationSettlementMapper settlements;

  public EntitlementSettlementServiceImpl(EntitlementGrantMapper grants,
      EntitlementLedgerMapper ledgers, GenerationSettlementMapper settlements) {
    this.grants = grants;
    this.ledgers = ledgers;
    this.settlements = settlements;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public SettlementResult consumeForTask(long taskId, long grantId, int units) {
    EntitlementGrant grant = lockAndVerify(taskId, grantId, units);
    freezeLedger(taskId, grant);
    if (grants.consumeFrozen(grantId, units) != 1) {
      throw new IllegalStateException("冻结余额与任务结算数量不一致");
    }
    EntitlementGrant after = requireGrant(grantId);
    if (after.getFrozenCredits() < 0) {
      throw new IllegalStateException("消费后冻结余额不能为负数");
    }
    EntitlementLedger ledger = ledger(after, taskId, CONSUME, 0, -units, units, 0,
        "任务结果交付完成，消费冻结次数");
    GenerationSettlement settlement = settlement(taskId, grantId, CONSUME, null, units,
        ledger.getId(), "结果交付完成");
    return result(CONSUME, null, units, ledger.getId(), after);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public SettlementResult releaseForTask(long taskId, long grantId, int units, String reason) {
    EntitlementGrant grant = lockAndVerify(taskId, grantId, units);
    freezeLedger(taskId, grant);
    // 有效期以数据库时间在持有权益包行锁时判定；到期清理任务与本次释放串行。
    LocalDateTime now = grants.selectDatabaseNow();
    boolean valid = ACTIVE.equals(grant.getStatus()) && grant.getExpiresAt().isAfter(now);
    String target = valid ? AVAILABLE : EXPIRED;
    int changed = valid ? grants.releaseFrozenToAvailable(grantId, units)
        : grants.releaseFrozenToExpired(grantId, units);
    if (changed != 1) {
      throw new IllegalStateException("冻结余额与任务释放数量不一致");
    }
    EntitlementGrant after = requireGrant(grantId);
    if (after.getFrozenCredits() < 0) {
      throw new IllegalStateException("释放后冻结余额不能为负数");
    }
    int deltaAvailable = valid ? units : 0;
    int deltaExpired = valid ? 0 : units;
    String note = valid ? "任务失败或取消，冻结次数退回可用"
        : "任务失败或取消，权益包已过期，冻结次数记入已过期";
    EntitlementLedger ledger = ledger(after, taskId, RELEASE, deltaAvailable, -units,
        0, deltaExpired, appendReason(note, reason));
    GenerationSettlement settlement = settlement(taskId, grantId, RELEASE, target, units,
        ledger.getId(), appendReason("任务最终释放", reason));
    return result(RELEASE, target, units, ledger.getId(), after);
  }

  private EntitlementGrant lockAndVerify(long taskId, long grantId, int units) {
    if (taskId <= 0 || grantId <= 0 || units <= 0) {
      throw new IllegalArgumentException("结算必须由任务、权益包和正数单位组成");
    }
    if (settlements.selectByTaskId(taskId) != null) {
      throw new IllegalStateException("任务已经存在最终结算");
    }
    EntitlementGrant grant = grants.selectForUpdate(grantId);
    if (grant == null) {
      throw new IllegalStateException("结算权益包不存在");
    }
    return grant;
  }

  private EntitlementLedger freezeLedger(long taskId, EntitlementGrant grant) {
    EntitlementLedger freeze =
        ledgers.selectByOperationKey(EntitlementKeys.freezeOperation(taskId));
    if (freeze == null || !grant.getId().equals(freeze.getGrantId())
        || !grant.getUserId().equals(freeze.getUserId())
        || !FREEZE.equals(freeze.getOperationType())) {
      throw new IllegalStateException("任务缺少可信的冻结流水，不能结算");
    }
    return freeze;
  }

  private EntitlementGrant requireGrant(long grantId) {
    EntitlementGrant after = grants.selectById(grantId);
    if (after == null) {
      throw new IllegalStateException("结算后权益包不存在");
    }
    return after;
  }

  private EntitlementLedger ledger(EntitlementGrant grant, long taskId,
      String type, int deltaAvailable, int deltaFrozen, int deltaConsumed, int deltaExpired,
      String reason) {
    EntitlementLedger ledger = new EntitlementLedger();
    ledger.setGrantId(grant.getId());
    ledger.setUserId(grant.getUserId());
    ledger.setOrderId(grant.getOrderId());
    ledger.setItemKey(grant.getItemKey());
    ledger.setOperationKey(CONSUME.equals(type)
        ? EntitlementKeys.consumeOperation(taskId)
        : EntitlementKeys.releaseOperation(taskId));
    ledger.setOperationType(type);
    ledger.setDeltaTotal(0);
    ledger.setDeltaAvailable(deltaAvailable);
    ledger.setDeltaFrozen(deltaFrozen);
    ledger.setDeltaConsumed(deltaConsumed);
    ledger.setDeltaExpired(deltaExpired);
    ledger.setTotalAfter(grant.getTotalCredits());
    ledger.setAvailableAfter(grant.getAvailableCredits());
    ledger.setFrozenAfter(grant.getFrozenCredits());
    ledger.setConsumedAfter(grant.getConsumedCredits());
    ledger.setExpiredAfter(grant.getExpiredCredits());
    ledger.setReason(reason);
    ledger.setReferenceType(TASK_REFERENCE);
    ledger.setReferenceId(taskId);
    ledgers.insert(ledger);
    return ledger;
  }

  private GenerationSettlement settlement(long taskId, long grantId, String kind,
      String target, int units, long ledgerId, String reason) {
    GenerationSettlement settlement = new GenerationSettlement();
    settlement.setTaskId(taskId);
    settlement.setGrantId(grantId);
    settlement.setKind(kind);
    settlement.setReleaseTarget(target);
    settlement.setUnits(units);
    settlement.setLedgerId(ledgerId);
    settlement.setReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
    settlements.insert(settlement);
    return settlement;
  }

  private SettlementResult result(String kind, String target, int units, long ledgerId,
      EntitlementGrant after) {
    return new SettlementResult(kind, target, units, ledgerId, after.getTotalCredits(),
        after.getAvailableCredits(), after.getFrozenCredits(), after.getConsumedCredits(),
        after.getExpiredCredits());
  }

  private String appendReason(String base, String reason) {
    if (reason == null || reason.isBlank()) {
      return base;
    }
    String merged = base + "：" + reason.trim();
    return merged.length() > 255 ? merged.substring(0, 255) : merged;
  }
}
