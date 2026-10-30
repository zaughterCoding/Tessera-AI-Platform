package com.aiplatform.common.api;

import com.aiplatform.cache.CacheUnavailableException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {
  /** 供审计拦截器读取已解析的业务错误码，不写入响应头或日志。 */
  public static final String ERROR_CODE_ATTRIBUTE = WebExceptionAdvice.class.getName() + ".code";

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

  // Spring Framework 6 对无匹配处理器和缺失静态资源抛出专用异常，需保持既有结构化 404。
  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  public ResponseEntity<Result<Void>> notFound() {
    return reject(ErrorCode.NOT_FOUND);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Result<Void>> tooLarge() {
    return reject(ErrorCode.PAYLOAD_TOO_LARGE);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Result<Void>> unauthenticated() {
    return reject(ErrorCode.UNAUTHENTICATED);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Result<Void>> forbidden() {
    return reject(ErrorCode.FORBIDDEN);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<Result<Void>> unavailable() {
    return reject(ErrorCode.UNAVAILABLE);
  }

  @ExceptionHandler(CacheUnavailableException.class)
  public ResponseEntity<Result<Void>> cacheUnavailable() {
    return reject(ErrorCode.UNAVAILABLE);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> unexpected(Exception error) {
    // 不记录异常载荷、请求体或凭据，排查时使用请求编号关联安全诊断。
    log.error("请求失败，requestId={}，type={}", MDC.get("requestId"),
        error.getClass().getName());
    return reject(ErrorCode.INTERNAL_ERROR);
  }

  private ResponseEntity<Result<Void>> reject(ErrorCode error) {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes) {
      ((ServletRequestAttributes) attributes).getRequest()
          .setAttribute(ERROR_CODE_ATTRIBUTE, error.name());
    }
    return ResponseEntity.status(error.getStatus()).body(Result.fail(error));
  }
}
