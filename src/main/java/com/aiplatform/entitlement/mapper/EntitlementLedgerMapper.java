package com.aiplatform.entitlement.mapper;

import com.aiplatform.entitlement.entity.EntitlementLedger;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 账务流水只暴露追加与读取，产品路径没有改写或删除历史的方法。 */
@Mapper
public interface EntitlementLedgerMapper {
  int insert(EntitlementLedger ledger);

  List<EntitlementLedger> selectByGrant(@Param("grantId") long grantId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countByGrant(@Param("grantId") long grantId);
}
