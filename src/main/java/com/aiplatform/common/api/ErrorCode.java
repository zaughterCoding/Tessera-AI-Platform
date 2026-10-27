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
  TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
  UNAVAILABLE(503, "服务暂时不可用"),
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
