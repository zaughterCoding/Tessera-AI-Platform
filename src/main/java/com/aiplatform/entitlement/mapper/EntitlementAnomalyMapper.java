package com.aiplatform.entitlement.mapper;

import com.aiplatform.entitlement.entity.EntitlementAnomaly;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EntitlementAnomalyMapper {
  int upsert(@Param("orderId") long orderId,
      @Param("userId") long userId,
      @Param("itemKey") String itemKey,
      @Param("anomalyType") String anomalyType,
      @Param("detail") String detail);

  int resolve(@Param("orderId") long orderId, @Param("itemKey") String itemKey);

  List<EntitlementAnomaly> selectList(@Param("status") String status,
      @Param("limit") int limit);

  long countOpen();
}
