package com.aiplatform.generation.provider;

import com.aiplatform.generation.config.GenerationProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 可选的模拟回调发送；地址只来自服务端配置，失败不影响轮询兜底。 */
@Slf4j
@Component
public class SimulatedProviderCallbackSender {
  private final GenerationProperties properties;
  private final ObjectMapper json;
  private final HttpClient client;

  public SimulatedProviderCallbackSender(GenerationProperties properties, ObjectMapper json) {
    this.properties = properties;
    this.json = json;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getSimulatorCallbackTimeoutMs()))
        .build();
  }

  public void notifyTerminal(String attemptKey, String providerTaskId, String state,
      String resultRef, String resultDigest, String failureCode, String failureDetail,
      List<ProviderResultRef> results) {
    if (!properties.isSimulatorCallbackEnabled()) {
      return;
    }
    try {
      String eventKey = providerTaskId + ":" + state;
      ProviderCallbackPayload payload = new ProviderCallbackPayload(eventKey, state, attemptKey,
          providerTaskId, resultRef, resultDigest, failureCode, failureDetail, results);
      byte[] body = json.writeValueAsBytes(payload);
      long timestamp = Instant.now().getEpochSecond();
      String signature = ProviderCallbackSignatures.sign(properties.getCallbackSecret(),
          properties.getChannelCode(), timestamp, eventKey, body);
      HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getSimulatorCallbackUrl()))
          .timeout(Duration.ofMillis(properties.getSimulatorCallbackTimeoutMs()))
          .header("Content-Type", "application/json")
          .header("X-Provider-Channel", properties.getChannelCode())
          .header("X-Provider-Timestamp", Long.toString(timestamp))
          .header("X-Provider-Signature", signature)
          .POST(HttpRequest.BodyPublishers.ofByteArray(body))
          .build();
      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() >= 300) {
        log.warn("模拟回调未确认，status={}", response.statusCode());
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      log.warn("模拟回调被中断，等待轮询恢复");
    } catch (Exception failure) {
      log.warn("模拟回调发送失败，等待轮询恢复，type={}", failure.getClass().getSimpleName());
    }
  }
}
