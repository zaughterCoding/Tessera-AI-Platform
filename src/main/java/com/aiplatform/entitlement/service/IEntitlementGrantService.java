package com.aiplatform.entitlement.service;

import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.trade.entity.PurchaseOrder;

public interface IEntitlementGrantService {
  /** 订单购买事实投影时同事务登记独立发放任务。 */
  void registerForOrder(PurchaseOrder order);

  /** 重新核对可信来源后发放；重复投递只返回同一权益包。 */
  EntitlementGrant grantForTask(BusinessOutboxTask task);

  /** 只把剩余可用次数转为已过期，返回实际变化的权益包数量。 */
  int expireDueGrants(int batch);
}
