package com.aiplatform.media.enums;

/** PENDING 表示配额已预占但文件尚未确认完整；只有 ACTIVE 可被读取或引用。 */
public enum MediaState {
  PENDING,
  ACTIVE,
  DELETING,
  DELETED,
  FAILED
}
