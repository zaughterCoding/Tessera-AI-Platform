package com.aiplatform.common.api;

import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<Result<Void>> business(ApiException error) {
    return reject(error.getErrorCode());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
      HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class, BindException.class,
      HttpMediaTypeNotSupportedException.class})
  public ResponseEntity<Result<Void>> invalid(Exception error) {
    return reject(ErrorCode.INVALID_REQUEST);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Result<Void>> methodNotAllowed() {
    return reject(ErrorCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<Result<Void>> unavailable() {
    return reject(ErrorCode.UNAVAILABLE);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> unexpected(Exception error) {
    // 不记录异常载荷、请求体或凭据，排查时使用请求编号关联安全诊断。
    log.error("请求失败，requestId={}，type={}", MDC.get("requestId"), error.getClass().getName());
    return reject(ErrorCode.INTERNAL_ERROR);
  }

  private ResponseEntity<Result<Void>> reject(ErrorCode error) {
    return ResponseEntity.status(error.getStatus()).body(Result.fail(error));
  }
}
