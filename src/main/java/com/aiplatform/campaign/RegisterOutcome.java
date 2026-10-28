package com.aiplatform.campaign;

/** 注册结果决定消息是继续履约、确认还是隔离；数据库状态永远优先于消息字段。 */
public enum RegisterOutcome {
  PROCESSING,
  SUCCESS,
  FAILED,
  REVIEW,
  STALE,
  INVALID
}
