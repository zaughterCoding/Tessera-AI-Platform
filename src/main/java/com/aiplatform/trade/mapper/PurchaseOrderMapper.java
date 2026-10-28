package com.aiplatform.trade.mapper;

import com.aiplatform.trade.dto.OrderProductSnapshot;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.entity.PurchaseRequest;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
  OrderProductSnapshot selectOrderProduct(@Param("productId") long productId);

  int insertRequestIfAbsent(@Param("userId") long userId,
      @Param("requestKey") String requestKey,
      @Param("requestDigest") String requestDigest);

  PurchaseRequest selectRequestForUpdate(@Param("userId") long userId,
      @Param("requestKey") String requestKey);

  int bindRequestOrder(@Param("requestId") long requestId,
      @Param("orderId") long orderId);

  int insertOrder(@Param("order") PurchaseOrder order,
      @Param("free") boolean free,
      @Param("timeoutMinutes") int timeoutMinutes);

  int insertCampaignOrder(@Param("order") PurchaseOrder order,
      @Param("free") boolean free,
      @Param("timeoutMinutes") int timeoutMinutes);

  PurchaseOrder selectOrderForUpdate(@Param("id") long id);

  int markPaid(@Param("id") long id,
      @Param("userId") long userId,
      @Param("amount") long amount);

  int closeExpired(@Param("id") long id);

  List<Long> selectExpiredOrderIds(@Param("limit") int limit);

  int insertOrderEvent(@Param("eventId") long eventId,
      @Param("orderId") long orderId,
      @Param("eventType") String eventType,
      @Param("occurredAt") LocalDateTime occurredAt);
}
