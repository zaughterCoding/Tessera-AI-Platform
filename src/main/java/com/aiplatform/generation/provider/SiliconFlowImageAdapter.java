package com.aiplatform.generation.provider;

import com.aiplatform.generation.ProviderResultMetadata;
import com.aiplatform.generation.ResultManifest;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.config.HttpProviderProperties;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SiliconFlow 同步图片接口：提交响应直接带结果地址，没有上游任务可查、不支持幂等和取消。
 * 响应丢失时无法确认是否出图，任务保持冻结并转人工核对；结果摘要由平台下载时计算。
 */
@Slf4j
@Component
public class SiliconFlowImageAdapter implements ProviderChannelAdapter {
  private static final int MAX_DETAIL = 120;

  private final HttpProviderProperties properties;
  private final GenerationProperties generation;
  private final ProviderCallBudget budget;
  private final ObjectMapper json;
  private final ProviderHttpExchange exchange;

  public SiliconFlowImageAdapter(HttpProviderProperties properties,
      GenerationProperties generation, ProviderCallBudget budget, ObjectMapper json) {
    this.properties = properties;
    this.generation = generation;
    this.budget = budget;
    this.json = json;
    this.exchange = new ProviderHttpExchange(properties);
  }

  @Override
  public String channelCode() {
    return generation.getChannelCode();
  }

  @Override
  public ProviderCapabilities capabilities() {
    return new ProviderCapabilities(false, false, false);
  }

  @Override
  public ProviderSubmitResult submit(ProviderSubmitCommand command) {
    if (!properties.isReady()) {
      throw new ProviderTemporaryException("PROVIDER_NOT_CONFIGURED", "真实渠道未启用或未配置");
    }
    if (command.mediaCount() > 0) {
      // 参考图不会传给上游；明确拒绝，不生成一张与用户输入不符的结果。
      throw new ProviderRejectedException("REFERENCE_IMAGE_UNSUPPORTED", "当前渠道不支持参考图");
    }
    if (!budget.tryReserve(channelCode(), properties.getRealCallLimit())) {
      throw new ProviderRejectedException("CALL_BUDGET_EXHAUSTED", "本地真实调用上限已用尽");
    }
    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.submitUri()))
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("User-Agent", "ai-creation-platform/1.0")
        .header("Authorization", "Bearer " + properties.getApiKey())
        .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
        .POST(HttpRequest.BodyPublishers.ofString(body(command)))
        .build();
    ProviderHttpExchange.Response response = exchange.send(request,
        properties.getRequestTimeoutMs());
    int status = response.status();
    if (status >= 200 && status < 300) {
      return succeeded(command, response.body());
    }
    // 503 是上游明确表示过载、未处理这次请求，和 429 一样可以安全重排。
    if (status == 429 || status == 503) {
      throw new ProviderThrottledException(response.retryAfterSeconds());
    }
    if (status == 401 || status == 403) {
      throw new ProviderRejectedException("PROVIDER_UNAUTHORIZED", "上游凭据无效或未授权");
    }
    if (status == 400 || status == 404 || status == 413 || status == 422) {
      throw new ProviderRejectedException("PROVIDER_REJECTED",
          "提示词或参数被上游拒绝：" + detail(response.body()));
    }
    log.warn("真实渠道返回未预期状态，status={}", status);
    throw new ProviderTemporaryException("PROVIDER_UNEXPECTED_STATUS", "上游返回未预期状态");
  }

  /** 同步接口没有任务可查；已成功的结果在提交时已登记，其余情况交由既有规则处理。 */
  @Override
  public ProviderQueryResult query(String attemptKey, String providerTaskId) {
    return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.NOT_FOUND, providerTaskId);
  }

  @Override
  public ProviderCancelResult cancel(String attemptKey, String providerTaskId) {
    return new ProviderCancelResult(ProviderCancelResult.CancelStatus.UNSUPPORTED, null);
  }

  private String body(ProviderSubmitCommand command) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", properties.getModel());
    payload.put("prompt", command.prompt() == null ? "" : command.prompt());
    payload.put("image_size", command.imageSize());
    payload.put("batch_size", command.outputCount());
    if (command.negativePrompt() != null && !command.negativePrompt().isBlank()) {
      payload.put("negative_prompt", command.negativePrompt());
    }
    return json.writeValueAsString(payload);
  }

  private ProviderSubmitResult succeeded(ProviderSubmitCommand command, byte[] body) {
    List<ProviderResultRef> refs = new ArrayList<>();
    try {
      JsonNode images = json.readTree(body).get("images");
      if (images != null && images.isArray()) {
        for (JsonNode image : images) {
          JsonNode url = image.get("url");
          refs.add(new ProviderResultRef(refs.size(), url == null ? null : url.asText(),
              ProviderResultMetadata.DIGEST_UNDECLARED));
        }
      }
    } catch (RuntimeException invalid) {
      refs.clear();
    }
    // 成功响应却拿不到完整结果：上游可能已出图，不猜测失败，也不自动退款。
    if (ResultManifest.complete(refs, command.outputCount()) == null) {
      throw new ProviderUncertainException("PROVIDER_RESPONSE_INVALID", "上游响应缺少完整结果地址");
    }
    ProviderResultRef first = refs.get(0);
    ProviderQueryResult result = new ProviderQueryResult(ProviderQueryResult.QueryStatus.SUCCEEDED,
        command.attemptKey(), first.ref(), first.digest(), null, null, List.copyOf(refs));
    return new ProviderSubmitResult("RUNNING", command.attemptKey(), result);
  }

  /** 只取上游 message 字段并截断，不回显完整响应。 */
  private String detail(byte[] body) {
    String message = null;
    try {
      JsonNode node = json.readTree(body);
      JsonNode value = node == null ? null : node.get("message");
      message = value == null || value.isNull() ? null : value.asText();
    } catch (RuntimeException ignored) {
      // 错误响应不是 JSON 时使用通用说明。
    }
    if (message == null || message.isBlank()) {
      return "未给出原因";
    }
    String clean = message.replaceAll("[\\r\\n\\t]", " ").trim();
    return clean.length() <= MAX_DETAIL ? clean : clean.substring(0, MAX_DETAIL);
  }
}
