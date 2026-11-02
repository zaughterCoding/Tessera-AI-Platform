package com.aiplatform.generation.provider;

import com.aiplatform.generation.config.HttpProviderProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 真实渠道的受控 HTTP 交换：不跟随重定向，响应体读取受总时限与体积上限约束。
 * JDK 的请求超时不覆盖正文读取，因此由单一守护线程在到期后关闭响应流。
 */
final class ProviderHttpExchange {
  private static final ScheduledExecutorService WATCHDOG =
      Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "provider-http-watchdog");
        thread.setDaemon(true);
        return thread;
      });
  private static final int BUFFER = 4096;

  private final HttpProviderProperties properties;
  private final HttpClient client;

  ProviderHttpExchange(HttpProviderProperties properties) {
    this.properties = properties;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  Response send(HttpRequest request, long timeoutMs) {
    HttpResponse<InputStream> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (java.net.http.HttpTimeoutException timeout) {
      throw new ProviderTemporaryException("PROVIDER_TIMEOUT", "上游请求超时");
    } catch (IOException failure) {
      throw new ProviderTemporaryException("PROVIDER_IO", "上游连接失败");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProviderTemporaryException("PROVIDER_INTERRUPTED", "上游请求被中断");
    }
    return new Response(response.statusCode(), read(response, timeoutMs),
        retryAfter(response.headers().firstValue("Retry-After").orElse(null)));
  }

  private byte[] read(HttpResponse<InputStream> response, long timeoutMs) {
    InputStream body = response.body();
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    AtomicBoolean expired = new AtomicBoolean();
    ScheduledFuture<?> guard = WATCHDOG.schedule(() -> {
      expired.set(true);
      closeQuietly(body);
    }, timeoutMs, TimeUnit.MILLISECONDS);
    try (body) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[BUFFER];
      int count;
      while ((count = body.read(buffer)) != -1) {
        out.write(buffer, 0, count);
        if (out.size() > properties.getMaxResponseBytes()) {
          throw new ProviderTemporaryException("PROVIDER_RESPONSE_TOO_LARGE", "上游响应超过体积上限");
        }
        if (System.nanoTime() > deadline) {
          break;
        }
      }
      if (expired.get()) {
        throw new ProviderTemporaryException("PROVIDER_TIMEOUT", "上游响应读取超时");
      }
      return out.toByteArray();
    } catch (IOException failure) {
      if (expired.get()) {
        throw new ProviderTemporaryException("PROVIDER_TIMEOUT", "上游响应读取超时");
      }
      throw new ProviderTemporaryException("PROVIDER_IO", "上游响应读取失败");
    } finally {
      guard.cancel(false);
    }
  }

  private void closeQuietly(InputStream body) {
    try {
      body.close();
    } catch (IOException ignored) {
      // 关闭失败不影响超时判定，连接会由客户端最终回收。
    }
  }

  /** 只接受秒数形式；日期形式或非法值按没有给出处理。 */
  private Integer retryAfter(String value) {
    if (value == null || !value.trim().matches("\\d{1,6}")) {
      return null;
    }
    return Integer.valueOf(value.trim());
  }

  record Response(int status, byte[] body, Integer retryAfterSeconds) {
  }
}
