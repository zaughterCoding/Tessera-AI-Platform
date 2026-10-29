package com.aiplatform.operations.audit;

import com.aiplatform.common.api.WebExceptionAdvice;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.operations.entity.OperationAudit;
import com.aiplatform.operations.service.IOperationAuditService;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 管理写操作完成后记录真实结果；不读取请求体，也不把"请求已发送"当作成功。
 */
@Slf4j
@Component
public class OperationAuditInterceptor implements HandlerInterceptor {
  private final IOperationAuditService audits;

  public OperationAuditInterceptor(IOperationAuditService audits) {
    this.audits = audits;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception failure) {
    if (!(handler instanceof HandlerMethod)) {
      return;
    }
    AuditOperation mark = ((HandlerMethod) handler).getMethodAnnotation(AuditOperation.class);
    if (mark == null) {
      return;
    }
    UserDTO operator = UserHolder.getUser();
    OperationAudit audit = new OperationAudit();
    audit.setRequestId(truncate((String) request.getAttribute("requestId"), 64));
    audit.setOperatorId(operator == null ? null : operator.getId());
    audit.setOperatorRoles(operator == null ? "" : truncate(
        operator.getRoles().stream().sorted().collect(Collectors.joining(",")), 128));
    audit.setAction(truncate(mark.action(), 64));
    audit.setObjectType(truncate(mark.objectType(), 64));
    audit.setObjectId(truncate(objectId(request, mark), 64));
    boolean success = failure == null && response.getStatus() < 400;
    audit.setOutcome(success ? "SUCCESS" : "FAILURE");
    audit.setErrorCode(success ? null
        : truncate((String) request.getAttribute(WebExceptionAdvice.ERROR_CODE_ATTRIBUTE), 64));
    try {
      audits.record(audit);
    } catch (RuntimeException writeFailure) {
      // 审计库不可用时保留业务结果；请求编号用于关联应用日志排查。
      log.warn("操作审计写入失败，requestId={}，type={}", audit.getRequestId(),
          writeFailure.getClass().getSimpleName());
    }
  }

  private String objectId(HttpServletRequest request, AuditOperation mark) {
    if (mark.objectIdParam() == null || mark.objectIdParam().isEmpty()) {
      return null;
    }
    Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(attribute instanceof Map)) {
      return null;
    }
    Object value = ((Map<?, ?>) attribute).get(mark.objectIdParam());
    return value == null ? null : String.valueOf(value);
  }

  private String truncate(String value, int length) {
    if (value == null) {
      return null;
    }
    return value.length() <= length ? value : value.substring(0, length);
  }
}
