package com.aiplatform.generation.service;

/** 回调处理结果；冲突与非法事件必须在不改动业务事实的前提下返回。 */
public enum CallbackOutcome {
  APPLIED,
  DUPLICATE,
  IGNORED,
  CONFLICT,
  INVALID
}
