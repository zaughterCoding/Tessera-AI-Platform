package com.aiplatform.entitlement.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.entitlement.EntitlementKeys;
import com.aiplatform.entitlement.GrantSpec;
import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.entitlement.entity.EntitlementLedger;
import com.aiplatform.entitlement.mapper.EntitlementGrantMapper;
import com.aiplatform.entitlement.mapper.EntitlementLedgerMapper;
import com.aiplatform.entitlement.service.IEntitlementReservationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 冻结只在调用方短事务内完成；余额、流水和任务事实一起提交或一起回滚。 */
@Service
public class EntitlementReservationServiceImpl implements IEntitlementReservationService {
  private static final int CANDIDATE_LIMIT = 8;
  private static final int LOCK_ATTEMPTS = 3;
  private static final long LOCK_RETRY_MILLIS = 15L;
  private static final String ACTIVE = "ACTIVE";
  private static final String FREEZE = "FREEZE";
  private static final String TASK_REFERENCE = "TASK";
  private final EntitlementGrantMapper grants;
  private final EntitlementLedgerMapper ledgers;

  public EntitlementReservationServiceImpl(
      EntitlementGrantMapper grants, EntitlementLedgerMapper ledgers) {
    this.grants = grants;
    this.ledgers = ledgers;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public EntitlementGrant lockGrantForSubmission(long userId, GrantSpec spec) {
    List<Long> candidates = grants.selectSubmissionCandidateIds(userId, spec.applicationId(),
        spec.applicationVersion(), spec.modelVersion(), spec.imageSize(), spec.outputCount(),
        CANDIDATE_LIMIT);
    if (candidates.isEmpty()) {
      throw unavailable(spec, userId);
    }
    boolean lockContended = false;
    for (Long id : candidates) {
      for (int attempt = 0; attempt < LOCK_ATTEMPTS; attempt++) {
        try {
          EntitlementGrant grant = grants.selectForUpdateNowait(id);
          if (grant == null) {
            break;
          }
          LocalDateTime now = grants.selectDatabaseNow();
          if (!usable(grant, userId, spec, now)) {
            break;
          }
          return grant;
        } catch (PessimisticLockingFailureException contended) {
          // NOWAIT 只终止当前语句；有限重试后明确返回繁忙，余额不受影响。
          lockContended = true;
          pause();
        }
      }
    }
    if (lockContended || !grants.selectSubmissionCandidateIds(userId, spec.applicationId(),
        spec.applicationVersion(), spec.modelVersion(), spec.imageSize(), spec.outputCount(),
        CANDIDATE_LIMIT).isEmpty()) {
      throw new ApiException(ErrorCode.SUBMISSION_BUSY);
    }
    throw unavailable(spec, userId);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void freezeForTask(EntitlementGrant grant, long taskId, int units) {
    if (grant == null || grant.getId() == null || taskId <= 0 || units != 1) {
      throw new IllegalArgumentException("冻结必须由已锁定的权益包和一个任务组成");
    }
    if (grants.freezeAvailable(grant.getId(), units) != 1) {
      throw new ApiException(ErrorCode.INSUFFICIENT_CREDITS);
    }
    int available = grant.getAvailableCredits() - units;
    int frozen = grant.getFrozenCredits() + units;
    if (available < 0) {
      throw new IllegalStateException("冻结后可用次数不能为负数");
    }
    grant.setAvailableCredits(available);
    grant.setFrozenCredits(frozen);
    EntitlementLedger ledger = new EntitlementLedger();
    ledger.setGrantId(grant.getId());
    ledger.setUserId(grant.getUserId());
    ledger.setOrderId(grant.getOrderId());
    ledger.setItemKey(grant.getItemKey());
    ledger.setOperationKey(EntitlementKeys.freezeOperation(taskId));
    ledger.setOperationType(FREEZE);
    ledger.setDeltaTotal(0);
    ledger.setDeltaAvailable(-units);
    ledger.setDeltaFrozen(units);
    ledger.setDeltaConsumed(0);
    ledger.setDeltaExpired(0);
    ledger.setTotalAfter(grant.getTotalCredits());
    ledger.setAvailableAfter(available);
    ledger.setFrozenAfter(frozen);
    ledger.setConsumedAfter(grant.getConsumedCredits());
    ledger.setExpiredAfter(grant.getExpiredCredits());
    ledger.setReason("提交生成任务冻结");
    ledger.setReferenceType(TASK_REFERENCE);
    ledger.setReferenceId(taskId);
    ledgers.insert(ledger);
  }

  private boolean usable(EntitlementGrant grant, long userId, GrantSpec spec, LocalDateTime now) {
    return Objects.equals(grant.getUserId(), userId)
        && ACTIVE.equals(grant.getStatus())
        && !grant.getEffectiveFrom().isAfter(now)
        && grant.getExpiresAt().isAfter(now)
        && grant.getAvailableCredits() != null
        && grant.getAvailableCredits() >= 1
        && Objects.equals(grant.getApplicationId(), spec.applicationId())
        && Objects.equals(grant.getApplicationVersion(), spec.applicationVersion())
        && Objects.equals(grant.getModelVersion(), spec.modelVersion())
        && Objects.equals(grant.getImageSize(), spec.imageSize())
        && Objects.equals(grant.getOutputCount(), spec.outputCount());
  }

  private ApiException unavailable(GrantSpec spec, long userId) {
    long matching = grants.countMatchingGrants(userId, spec.applicationId(),
        spec.applicationVersion(), spec.modelVersion(), spec.imageSize(), spec.outputCount());
    return matching == 0
        ? new ApiException(ErrorCode.SPEC_MISMATCH)
        : new ApiException(ErrorCode.INSUFFICIENT_CREDITS);
  }

  private void pause() {
    try {
      Thread.sleep(LOCK_RETRY_MILLIS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ApiException(ErrorCode.SUBMISSION_BUSY);
    }
  }
}
