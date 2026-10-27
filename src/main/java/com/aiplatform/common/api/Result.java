package com.aiplatform.common.api;

import java.util.List;
import lombok.Getter;
import org.slf4j.MDC;

@Getter
public class Result<T> {
  private final boolean success;
  private final String code;
  private final String errorMsg;
  private final T data;
  private final Long total;
  private final String requestId;

  private Result(boolean success, String code, String errorMsg, T data, Long total,
      String requestId) {
    this.success = success;
    this.code = code;
    this.errorMsg = errorMsg;
    this.data = data;
    this.total = total;
    this.requestId = requestId;
  }

  public static Result<Void> ok() {
    return ok(null);
  }

  public static <T> Result<T> ok(T data) {
    return new Result<>(true, "OK", null, data, null, MDC.get("requestId"));
  }

  public static <T> Result<List<T>> ok(List<T> data, Long total) {
    return new Result<>(true, "OK", null, data, total, MDC.get("requestId"));
  }

  public static Result<Void> fail(ErrorCode error) {
    return fail(error, MDC.get("requestId"));
  }

  public static Result<Void> fail(ErrorCode error, String requestId) {
    return new Result<>(false, error.name(), error.getMessage(), null, null, requestId);
  }
}
