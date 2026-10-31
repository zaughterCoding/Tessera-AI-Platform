package com.aiplatform.common.api;

import lombok.Getter;

@Getter
public enum ErrorCode {
  INVALID_REQUEST(400, "请求参数不合法"),
  INVALID_CODE(400, "验证码错误、已过期或已使用"),
  UNAUTHENTICATED(401, "请先登录"),
  FORBIDDEN(403, "当前账号没有操作权限"),
  NOT_FOUND(404, "资源不存在"),
  METHOD_NOT_ALLOWED(405, "请求方法不支持"),
  CONFLICT(409, "请求与当前状态冲突"),
  REQUEST_CONFLICT(409, "同一请求键已用于不同的业务请求"),
  INSUFFICIENT_CREDITS(409, "当前规格没有可用的权益次数"),
  SPEC_MISMATCH(409, "没有与所选应用、版本和规格匹配的权益包"),
  APPLICATION_UNAVAILABLE(409, "应用已下架或请求的版本已不可用"),
  QUOTA_EXCEEDED(409, "媒体存储配额不足"),
  PAYLOAD_TOO_LARGE(413, "文件大小或类型不合法"),
  TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
  UNAVAILABLE(503, "服务暂时不可用"),
  SUBMISSION_DISABLED(503, "任务提交当前未开启"),
  SUBMISSION_BUSY(503, "提交请求繁忙，请稍后重试"),
  DELIVERY_UNAVAILABLE(503, "验证码发送渠道尚未配置"),
  INTERNAL_ERROR(500, "服务器异常");

  private final int status;
  private final String message;

  ErrorCode(int status, String message) {
    this.status = status;
    this.message = message;
  }

  public static ErrorCode forStatus(int status) {
    for (ErrorCode error : values()) {
      if (error.status == status) {
        return error;
      }
    }
    return status < 500 ? INVALID_REQUEST : INTERNAL_ERROR;
  }
}
