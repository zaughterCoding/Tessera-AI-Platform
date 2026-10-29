package com.aiplatform.operations.retention;

/** 保留清理目标；每个目标只删除对应表中已终结且超过保留期的记录。 */
public enum RetentionTarget {
  BUSINESS_OUTBOX_DONE("business-outbox-done", "已完成业务 Outbox", "仅 DONE 且无失败复核引用"),
  MESSAGE_INBOX("message-inbox", "消息去重台账", "对应任务未完成时保留去重记录"),
  MESSAGE_FAILURE("message-failure", "消息失败隔离", "仍关联 REVIEW 任务时保留证据"),
  ANALYTICS_FAILURE("analytics-failure", "分析失败隔离", "超过保留期后截断"),
  OPERATION_AUDIT("operation-audit", "运营审计", "超过保留期后按主键顺序截断");

  private final String key;
  private final String label;
  private final String rule;

  RetentionTarget(String key, String label, String rule) {
    this.key = key;
    this.label = label;
    this.rule = rule;
  }

  public String key() {
    return key;
  }

  public String label() {
    return label;
  }

  public String rule() {
    return rule;
  }

  public static RetentionTarget fromKey(String key) {
    for (RetentionTarget target : values()) {
      if (target.key.equals(key)) {
        return target;
      }
    }
    throw new IllegalArgumentException("未知的保留清理目标");
  }
}
