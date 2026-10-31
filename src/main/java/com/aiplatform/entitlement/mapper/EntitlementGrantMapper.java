package com.aiplatform.entitlement.mapper;

import com.aiplatform.entitlement.dto.EntitlementBalanceDTO;
import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EntitlementGrantMapper extends BaseMapper<EntitlementGrant> {
  EntitlementGrant selectBySource(@Param("orderId") long orderId,
      @Param("itemKey") String itemKey);

  EntitlementGrant selectForUpdate(@Param("id") long id);

  /** 选包时只锁定单个候选行；拿不到锁由调用方按繁忙处理，不误报余额不足。 */
  EntitlementGrant selectForUpdateNowait(@Param("id") long id);

  List<Long> selectSubmissionCandidateIds(@Param("userId") long userId,
      @Param("applicationId") long applicationId,
      @Param("applicationVersion") int applicationVersion,
      @Param("modelVersion") String modelVersion,
      @Param("imageSize") String imageSize,
      @Param("outputCount") int outputCount,
      @Param("limit") int limit);

  /** 统计同规格权益包，用于区分“从未购买该规格”和“当前无可用次数”。 */
  long countMatchingGrants(@Param("userId") long userId,
      @Param("applicationId") long applicationId,
      @Param("applicationVersion") int applicationVersion,
      @Param("modelVersion") String modelVersion,
      @Param("imageSize") String imageSize,
      @Param("outputCount") int outputCount);

  int freezeAvailable(@Param("id") long id, @Param("units") int units);

  LocalDateTime selectDatabaseNow();

  List<PurchaseOrder> selectPaidOrdersAfter(@Param("afterOrderId") long afterOrderId,
      @Param("limit") int limit);

  long countPaidOrdersAfter(@Param("afterOrderId") long afterOrderId);

  long countTaskByOrder(@Param("orderId") long orderId);

  int expireAvailable(@Param("id") long id, @Param("amount") int amount);

  int markExpired(@Param("id") long id);

  List<Long> selectDueIds(@Param("limit") int limit);

  EntitlementBalanceDTO selectBalance(@Param("userId") long userId);

  long countEligibleOrderItems();

  long countRegisteredTasks();

  long countIssuedGrants();

  long countPendingTasks();

  long countReviewTasks();

  long countFailedMessages();

  long countExpiredGrants();

  long countMissingTasks();

  long countDoneWithoutGrant();

  long countDuplicateGrants();

  long countBalanceViolations();

  long countLedgerMismatches();
}
