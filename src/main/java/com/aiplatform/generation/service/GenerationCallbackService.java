package com.aiplatform.generation.service;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.dto.ProviderCallbackResultDTO;
import com.aiplatform.generation.provider.ProviderCallbackPayload;
import com.aiplatform.generation.provider.ProviderCallbackSignatures;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 回调不依赖用户会话；渠道、时间戳、签名与载荷全部验证后才允许触碰业务事实。 */
@Service
public class GenerationCallbackService {
  private static final Pattern EVENT_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
  private static final Pattern ATTEMPT_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
  private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

  private final GenerationProperties properties;
  private final GenerationCallbackTransactions transactions;
  private final ObjectMapper json;

  public GenerationCallbackService(GenerationProperties properties,
      GenerationCallbackTransactions transactions, ObjectMapper json) {
    this.properties = properties;
    this.transactions = transactions;
    this.json = json;
  }

  public ProviderCallbackResultDTO handle(String channelHeader, String timestampHeader,
      String signature, byte[] body) {
    if (properties.getCallbackSecret() == null || properties.getCallbackSecret().isEmpty()) {
      throw new ApiException(ErrorCode.CALLBACK_NOT_CONFIGURED);
    }
    if (channelHeader == null || !properties.getChannelCode().equals(channelHeader)) {
      throw new ApiException(ErrorCode.CALLBACK_UNAUTHORIZED);
    }
    if (body == null || body.length == 0) {
      throw new ApiException(ErrorCode.CALLBACK_INVALID);
    }
    if (body.length > properties.getCallbackMaxBytes()) {
      throw new ApiException(ErrorCode.CALLBACK_TOO_LARGE);
    }
    long timestamp = timestamp(timestampHeader);
    long now = Instant.now().getEpochSecond();
    if (Math.abs(now - timestamp) > properties.getCallbackMaxSkewSeconds()) {
      throw new ApiException(ErrorCode.CALLBACK_UNAUTHORIZED);
    }
    ProviderCallbackPayload payload;
    try {
      payload = json.readValue(body, ProviderCallbackPayload.class);
    } catch (RuntimeException invalid) {
      throw new ApiException(ErrorCode.CALLBACK_INVALID);
    }
    // 事件键参与签名，先做结构校验；其余字段必须在验签通过后才可信。
    if (payload == null || payload.eventKey() == null
        || !EVENT_KEY.matcher(payload.eventKey()).matches()) {
      throw new ApiException(ErrorCode.CALLBACK_INVALID);
    }
    String expected = ProviderCallbackSignatures.sign(properties.getCallbackSecret(),
        properties.getChannelCode(), timestamp, payload.eventKey(), body);
    if (!ProviderCallbackSignatures.matches(expected,
        signature == null ? null : signature.toLowerCase(java.util.Locale.ROOT))) {
      throw new ApiException(ErrorCode.CALLBACK_UNAUTHORIZED);
    }
    validate(payload);
    String digest = ProviderCallbackSignatures.sha256(body);
    CallbackOutcome outcome = transactions.apply(properties.getChannelCode(), payload, digest,
        body.length);
    if (outcome == CallbackOutcome.CONFLICT) {
      throw new ApiException(ErrorCode.CALLBACK_CONFLICT);
    }
    if (outcome == CallbackOutcome.INVALID) {
      throw new ApiException(ErrorCode.CALLBACK_INVALID);
    }
    return new ProviderCallbackResultDTO(outcome.name());
  }

  private long timestamp(String value) {
    if (value == null || !value.matches("\\d{1,12}")) {
      throw new ApiException(ErrorCode.CALLBACK_UNAUTHORIZED);
    }
    return Long.parseLong(value);
  }

  private void validate(ProviderCallbackPayload payload) {
    if (payload == null || payload.eventKey() == null || payload.attemptKey() == null
        || payload.eventType() == null
        || !EVENT_KEY.matcher(payload.eventKey()).matches()
        || !ATTEMPT_KEY.matcher(payload.attemptKey()).matches()
        || !GenerationCallbackTransactions.supportedEvent(payload.eventType())
        || tooLong(payload.providerTaskId(), 128)
        || tooLong(payload.resultRef(), 255)
        || tooLong(payload.failureCode(), 48)
        || tooLong(payload.failureDetail(), 255)) {
      throw new ApiException(ErrorCode.CALLBACK_INVALID);
    }
    if (payload.resultDigest() != null
        && !DIGEST.matcher(payload.resultDigest()).matches()) {
      throw new ApiException(ErrorCode.CALLBACK_INVALID);
    }
  }

  private boolean tooLong(String value, int max) {
    return value != null && value.length() > max;
  }
}
