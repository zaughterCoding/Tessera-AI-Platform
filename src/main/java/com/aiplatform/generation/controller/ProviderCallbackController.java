package com.aiplatform.generation.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.dto.ProviderCallbackResultDTO;
import com.aiplatform.generation.service.GenerationCallbackService;
import com.aiplatform.identity.security.PublicEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 上游回调入口；只接受已签名事件，不接受用户身份、余额或任意任务状态字段。 */
@RestController
@RequestMapping("/generation/provider")
public class ProviderCallbackController {
  private final GenerationCallbackService callbacks;
  private final GenerationProperties properties;

  public ProviderCallbackController(GenerationCallbackService callbacks,
      GenerationProperties properties) {
    this.callbacks = callbacks;
    this.properties = properties;
  }

  @PublicEndpoint
  @PostMapping("/callback")
  public Result<ProviderCallbackResultDTO> callback(
      @RequestHeader(name = "X-Provider-Channel", required = false) String channel,
      @RequestHeader(name = "X-Provider-Timestamp", required = false) String timestamp,
      @RequestHeader(name = "X-Provider-Signature", required = false) String signature,
      HttpServletRequest request) {
    byte[] body;
    try {
      body = request.getInputStream().readNBytes(properties.getCallbackMaxBytes() + 1);
    } catch (IOException failure) {
      throw new ApiException(ErrorCode.CALLBACK_INVALID);
    }
    return Result.ok(callbacks.handle(channel, timestamp, signature, body));
  }
}
