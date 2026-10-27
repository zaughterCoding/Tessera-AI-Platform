package com.aiplatform.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

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
