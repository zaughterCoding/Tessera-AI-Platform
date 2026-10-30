package com.aiplatform.common.api;

import com.aiplatform.identity.security.PublicEndpoint;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicEndpoint
@RestController
public class ApiErrorController implements ErrorController {
  @RequestMapping("/error")
  public ResponseEntity<Result<Void>> error(HttpServletRequest request) {
    Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    int status = value instanceof Integer ? (Integer) value : 404;
    return ResponseEntity.status(status).body(Result.fail(
        ErrorCode.forStatus(status), (String) request.getAttribute("requestId")));
  }
}
