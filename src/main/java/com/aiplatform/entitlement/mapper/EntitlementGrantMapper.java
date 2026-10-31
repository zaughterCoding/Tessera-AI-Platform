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
