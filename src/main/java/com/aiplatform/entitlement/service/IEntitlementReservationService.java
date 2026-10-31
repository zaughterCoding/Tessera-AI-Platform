package com.aiplatform.entitlement.service;

import com.aiplatform.entitlement.GrantSpec;
import com.aiplatform.entitlement.entity.EntitlementGrant;

/** 供任务提交使用的内部冻结能力；不对外提供任意改余额或指定冻结次数的入口。 */
public interface IEntitlementReservationService {
  /**
   * 在调用方事务内按固定有效期与主键顺序选择并锁定一个匹配权益包。
   * 锁竞争返回繁忙，不解释成余额不足。
   */
  EntitlementGrant lockGrantForSubmission(long userId, GrantSpec spec);

  /** 同一事务内把一个可用单位转为冻结，并追加引用任务的 FREEZE 流水。 */
  void freezeForTask(EntitlementGrant grant, long taskId, int units);
}
