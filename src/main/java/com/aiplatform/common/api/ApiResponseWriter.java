package com.aiplatform.common.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiResponseWriter {
  private final ObjectMapper mapper;

  public ApiResponseWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void reject(HttpServletResponse response, ErrorCode error) throws IOException {
    response.setStatus(error.getStatus());
    response.setContentType("application/json;charset=UTF-8");
    mapper.writeValue(response.getOutputStream(), Result.fail(error));
  }
}
