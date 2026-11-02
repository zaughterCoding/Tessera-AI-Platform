package com.aiplatform.generation.provider;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.config.HttpProviderProperties;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 可配置的真实图片渠道适配器。默认不启用、调用上限为 0，未显式配置时不发起任何外部请求。
 * 协议为服务端与上游之间的提交/查询契约；供应商差异通过地址、路径、模型与凭据配置映射，
 * 能力声明默认保守，只有核实后才允许开启幂等重提或按尝试查询。
 */
@Slf4j
@Component
public class HttpImageProviderAdapter implements ProviderChannelAdapter {
  private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9_.-]{1,40}");
  private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
  private static final int MAX_REFERENCE_IMAGES = 9;

  private final HttpProviderProperties properties;
  private final GenerationProperties generation;
  private final ProviderCallBudget budget;
  private final ObjectMapper json;
  private final ProviderHttpExchange exchange;

  public HttpImageProviderAdapter(HttpProviderProperties properties,
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
    return new ProviderCapabilities(properties.isIdempotentSubmit(),
        properties.isQueryByAttempt(), properties.supportsCancel());
  }

  /** 只判断配置是否完整，不读取额度；用于已提交任务的查询恢复。 */
  public boolean isConfigured() {
    return properties.isReady();
  }

  /** 只有启用、已配置且仍有本地额度时才允许发起真实提交；额度不可读时同样不放行。 */
  public boolean isReady() {
    if (!isConfigured()) {
      return false;
    }
    int used = budget.used(channelCode());
    return used >= 0 && used < properties.getRealCallLimit();
  }

  @Override
  public ProviderSubmitResult submit(ProviderSubmitCommand command) {
    requireReady();
    if (!budget.tryReserve(channelCode(), properties.getRealCallLimit())) {
      throw new ProviderRejectedException("CALL_BUDGET_EXHAUSTED", "本地真实调用上限已用尽");
    }
    String body = submitBody(command);
    HttpRequest request = request(properties.submitUri(), true)
        .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
        .header("Idempotency-Key", command.attemptKey())
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    ProviderHttpExchange.Response response = exchange.send(request,
        properties.getRequestTimeoutMs());
    int status = response.status();
    if (status >= 200 && status < 300) {
      return accepted(response.body());
    }
    if (status == 429) {
      throw new ProviderThrottledException(response.retryAfterSeconds());
    }
    throw failure(status, response.body(), true);
  }

  @Override
  public ProviderQueryResult query(String attemptKey, String providerTaskId) {
    requireReady();
    if (providerTaskId == null || providerTaskId.isBlank()) {
      // 没有上游任务标识且渠道未声明按尝试查询：只能如实报告查询不到，交由既有规则处理。
      return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.NOT_FOUND, providerTaskId);
    }
    HttpRequest request = request(properties.taskUri(providerTaskId), false)
        .timeout(Duration.ofMillis(properties.getQueryTimeoutMs()))
        .GET()
        .build();
    ProviderHttpExchange.Response response = exchange.send(request,
        properties.getQueryTimeoutMs());
    int status = response.status();
    if (status == 404) {
      return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.NOT_FOUND, providerTaskId);
    }
    if (status < 200 || status >= 300) {
      throw failure(status, response.body(), false);
    }
    return queried(providerTaskId, response.body());
  }

  @Override
  public ProviderCancelResult cancel(String attemptKey, String providerTaskId) {
    if (!properties.supportsCancel()) {
      return new ProviderCancelResult(ProviderCancelResult.CancelStatus.UNSUPPORTED, null);
    }
    requireReady();
    if (providerTaskId == null || providerTaskId.isBlank()) {
      return new ProviderCancelResult(ProviderCancelResult.CancelStatus.NOT_FOUND, null);
    }
    HttpRequest request = request(properties.cancelUri(providerTaskId), true)
        .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build();
    ProviderHttpExchange.Response response = exchange.send(request,
        properties.getRequestTimeoutMs());
    int status = response.status();
    if (status >= 200 && status < 300) {
      return new ProviderCancelResult(ProviderCancelResult.CancelStatus.CANCELLED, "CANCELLED");
    }
    if (status == 404) {
      return new ProviderCancelResult(ProviderCancelResult.CancelStatus.NOT_FOUND, null);
    }
    if (status == 405 || status == 501) {
      return new ProviderCancelResult(ProviderCancelResult.CancelStatus.UNSUPPORTED, null);
    }
    if (status == 409) {
      return new ProviderCancelResult(ProviderCancelResult.CancelStatus.TERMINAL, "TERMINAL");
    }
    throw failure(status, response.body(), false);
  }

  private ProviderSubmitResult accepted(byte[] body) {
    JsonNode node = tree(body);
    String taskId = text(node, "taskId");
    if (taskId == null || taskId.isBlank() || taskId.length() > 128) {
      // 上游可能已经受理但响应不可信；按不确定处理，不猜测失败，也不自动退款。
      throw new ProviderUncertainException("PROVIDER_RESPONSE_INVALID", "上游受理响应缺少任务标识");
    }
    String status = text(node, "status");
    String state = "running".equalsIgnoreCase(status) ? "RUNNING" : "ACCEPTED";
    return new ProviderSubmitResult(state, taskId);
  }

  private ProviderQueryResult queried(String providerTaskId, byte[] body) {
    JsonNode node = tree(body);
    String status = text(node, "status");
    if (status == null) {
      throw new ProviderTemporaryException("PROVIDER_RESPONSE_INVALID", "上游查询响应缺少状态");
    }
    switch (status.toLowerCase(Locale.ROOT)) {
      case "queued", "pending", "accepted":
        return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.ACCEPTED, providerTaskId);
      case "running", "processing":
        return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.RUNNING, providerTaskId);
      case "succeeded", "success", "completed":
        return succeeded(providerTaskId, node);
      case "failed", "error":
        return new ProviderQueryResult(ProviderQueryResult.QueryStatus.FAILED, providerTaskId,
            null, null, code(node, "failureCode"), detail(node));
      case "cancelled", "canceled":
        return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.CANCELLED, providerTaskId);
      default:
        throw new ProviderTemporaryException("PROVIDER_STATUS_UNKNOWN", "上游返回未知任务状态");
    }
  }

  /** 成功必须带完整结果清单；缺少地址或摘要时只返回成功状态，由既有规则转入人工核对。 */
  private ProviderQueryResult succeeded(String providerTaskId, JsonNode node) {
    JsonNode outputs = node.get("outputs");
    if (outputs == null || !outputs.isArray() || outputs.isEmpty()) {
      return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.SUCCEEDED, providerTaskId);
    }
    List<ProviderResultRef> refs = new ArrayList<>();
    int position = 0;
    for (JsonNode output : outputs) {
      String ref = text(output, "url");
      String digest = text(output, "sha256");
      if (ref == null || digest == null || !DIGEST.matcher(digest).matches()) {
        return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.SUCCEEDED, providerTaskId);
      }
      refs.add(new ProviderResultRef(position, ref, digest));
      position++;
    }
    ProviderResultRef first = refs.get(0);
    return new ProviderQueryResult(ProviderQueryResult.QueryStatus.SUCCEEDED, providerTaskId,
        first.ref(), first.digest(), null, null, List.copyOf(refs));
  }

  private RuntimeException failure(int status, byte[] body, boolean submitting) {
    if (status == 401 || status == 403) {
      return new ProviderRejectedException("PROVIDER_UNAUTHORIZED", "上游凭据无效或未授权");
    }
    if (status == 429 || status == 408 || status == 425 || status >= 500) {
      log.warn("真实渠道暂时不可用，status={}", status);
      return new ProviderTemporaryException("PROVIDER_TEMPORARY", "上游暂时不可用");
    }
    if (submitting && (status == 400 || status == 404 || status == 409
        || status == 413 || status == 422)) {
      String code = errorCode(body);
      return new ProviderRejectedException("PROVIDER_REJECTED",
          code == null ? "上游拒绝请求" : "上游拒绝请求: " + code);
    }
    log.warn("真实渠道返回未预期状态，status={}", status);
    return new ProviderTemporaryException("PROVIDER_UNEXPECTED_STATUS", "上游返回未预期状态");
  }

  private HttpRequest.Builder request(String uri, boolean withBody) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
        .header("Accept", "application/json")
        .header("User-Agent", "ai-creation-platform/1.0");
    if (properties.getAuthScheme() == null || properties.getAuthScheme().isBlank()) {
      builder.header("Authorization", properties.getApiKey());
    } else {
      builder.header("Authorization",
          properties.getAuthScheme() + " " + properties.getApiKey());
    }
    if (withBody) {
      builder.header("Content-Type", "application/json");
    }
    return builder;
  }

  private String submitBody(ProviderSubmitCommand command) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("idempotencyKey", command.attemptKey());
    payload.put("model", properties.getModel());
    payload.put("size", command.imageSize());
    payload.put("count", command.outputCount());
    payload.put("prompt", command.prompt() == null ? "" : command.prompt());
    payload.put("negativePrompt",
        command.negativePrompt() == null ? "" : command.negativePrompt());
    payload.put("referenceImageCount",
        Math.min(Math.max(command.mediaCount(), 0), MAX_REFERENCE_IMAGES));
    return json.writeValueAsString(payload);
  }

  private JsonNode tree(byte[] body) {
    try {
      JsonNode node = json.readTree(body);
      if (node == null || !node.isObject()) {
        throw new ProviderTemporaryException("PROVIDER_RESPONSE_INVALID", "上游响应结构不合法");
      }
      return node;
    } catch (ProviderTemporaryException known) {
      throw known;
    } catch (RuntimeException invalid) {
      throw new ProviderTemporaryException("PROVIDER_RESPONSE_INVALID", "上游响应不可解析");
    }
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private String code(JsonNode node, String field) {
    JsonNode error = node.get("error");
    String value = text(error == null || error.isNull() ? node : error, field);
    if (value == null) {
      value = text(error, "code");
    }
    return value != null && SAFE_CODE.matcher(value).matches() ? value : null;
  }

  private String detail(JsonNode node) {
    JsonNode error = node.get("error");
    String value = text(error == null || error.isNull() ? node : error, "message");
    if (value == null) {
      return "上游报告失败";
    }
    String trimmed = value.replaceAll("[\\r\\n\\t]", " ").trim();
    return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
  }

  /** 错误内容只保留安全字符的错误码，绝不回显上游原始响应或请求凭据。 */
  private String errorCode(byte[] body) {
    if (body == null || body.length == 0) {
      return null;
    }
    try {
      JsonNode node = json.readTree(body);
      if (node == null || !node.isObject()) {
        return null;
      }
      JsonNode error = node.get("error");
      String value = error == null || error.isNull() ? text(node, "code") : text(error, "code");
      return value != null && SAFE_CODE.matcher(value).matches() ? value : null;
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void requireReady() {
    if (!properties.isReady()) {
      throw new ProviderTemporaryException("PROVIDER_NOT_CONFIGURED", "真实渠道未启用或未配置");
    }
  }
}
