package com.aiplatform.entitlement;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 任务与流水使用稳定业务键；重放和补发都落到同一身份上。 */
public final class EntitlementKeys {
  private EntitlementKeys() {}

  public static String grantTask(long orderId) {
    return "entitlement-grant:" + orderId + ":" + EntitlementItems.CREDITS;
  }

  public static String grantOperation(long orderId) {
    return "GRANT:" + orderId + ":" + EntitlementItems.CREDITS;
  }

  /** 同一权益包的到期处理只有一条流水；重复扫描不会追加第二条。 */
  public static String expireOperation(long grantId, LocalDateTime expiresAt) {
    long epochMillis = expiresAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    return "EXPIRE:" + grantId + ":" + epochMillis;
  }

  /** 一个任务只冻结一次；任务身份就是稳定的业务操作键。 */
  public static String freezeOperation(long taskId) {
    return "FREEZE:task:" + taskId + ":" + EntitlementItems.CREDITS;
  }

  /** 一个任务的最终结算只有一种去向；任务身份同时约束消费与释放。 */
  public static String consumeOperation(long taskId) {
    return "CONSUME:task:" + taskId + ":" + EntitlementItems.CREDITS;
  }

  public static String releaseOperation(long taskId) {
    return "RELEASE:task:" + taskId + ":" + EntitlementItems.CREDITS;
  }
}
