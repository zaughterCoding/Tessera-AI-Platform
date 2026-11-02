package com.aiplatform.generation.result;

import com.aiplatform.generation.ProviderResultMetadata;
import com.aiplatform.generation.config.ResultDeliveryProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 受控拉取上游结果：只允许 HTTP(S) 公网目标，逐跳校验重定向、目标地址、体积、类型与摘要。
 * 内网目标仅在显式测试配置与 local/test 环境下放行，生产配置校验会拒绝该开关。
 * 正文读取由独立看护线程按读取停滞与总时限关闭流，避免停滞上游长期占住保存线程。
 */
@Component
public class HttpResultFetcher {
  private static final Set<String> REDIRECTS = Set.of("301", "302", "303", "307", "308");
  private static final String UNDECLARED_TYPE = "application/octet-stream";
  private static final int BUFFER = 8192;
  // 单一守护线程看护所有正文读取，任务随读取结束取消，不会按请求增长线程。
  private static final ScheduledExecutorService WATCHDOG =
      Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "result-fetch-watchdog");
        thread.setDaemon(true);
        return thread;
      });

  private final ResultDeliveryProperties properties;
  private final HttpClient client;

  public HttpResultFetcher(ResultDeliveryProperties properties) {
    this.properties = properties;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
        .followRedirects(HttpClient.Redirect.NEVER)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
  }

  public FetchedResult fetch(String sourceRef, String expectedDigest) {
    URI uri = parse(sourceRef);
    long deadline = System.nanoTime()
        + Duration.ofMillis(properties.getTotalTimeoutMs()).toNanos();
    for (int hop = 0; hop <= properties.getMaxRedirects(); hop++) {
      validateTarget(uri);
      HttpResponse<InputStream> response = send(uri, deadline);
      int status = response.statusCode();
      if (REDIRECTS.contains(Integer.toString(status))) {
        closeQuietly(response.body());
        String location = response.headers().firstValue("Location").orElse(null);
        if (location == null || location.isBlank()) {
          throw new ResultFetchException("REDIRECT_INVALID", "重定向缺少目标");
        }
        if (hop == properties.getMaxRedirects()) {
          throw new ResultFetchException("REDIRECT_LIMIT", "重定向次数超限");
        }
        try {
          uri = uri.resolve(location.trim());
        } catch (IllegalArgumentException invalid) {
          throw new ResultFetchException("REDIRECT_INVALID", "重定向目标不合法");
        }
        continue;
      }
      if (status != 200) {
        closeQuietly(response.body());
        throw new ResultFetchException("HTTP_" + status, "结果下载返回非成功状态");
      }
      return read(response, expectedDigest, deadline);
    }
    throw new ResultFetchException("REDIRECT_LIMIT", "重定向次数超限");
  }

  private HttpResponse<InputStream> send(URI uri, long deadline) {
    long remaining = Math.min(properties.getTotalTimeoutMs(),
        Duration.ofNanos(Math.max(deadline - System.nanoTime(), 1)).toMillis());
    if (remaining <= 0) {
      throw new ResultFetchException("FETCH_TIMEOUT", "结果下载总时长超限");
    }
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(remaining))
        .header("Accept", "image/png,image/jpeg,image/webp")
        .header("Accept-Encoding", "identity")
        .header("User-Agent", "ai-creation-platform/1.0")
        .GET()
        .build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (java.net.http.HttpTimeoutException timeout) {
      throw new ResultFetchException("FETCH_TIMEOUT", "结果下载超时", timeout);
    } catch (IOException failure) {
      throw new ResultFetchException("FETCH_IO", "结果下载连接失败", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ResultFetchException("FETCH_INTERRUPTED", "结果下载被中断", interrupted);
    }
  }

  private FetchedResult read(HttpResponse<InputStream> response, String expectedDigest,
      long deadline) {
    long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
    if (declaredLength > properties.getMaxBytes()) {
      closeQuietly(response.body());
      throw new ResultFetchException("RESULT_TOO_LARGE", "结果文件超过体积上限");
    }
    String declaredType = response.headers().firstValue("Content-Type")
        .map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)).orElse("");
    byte[] buffer = new byte[BUFFER];
    ByteArrayOutputStream out = new ByteArrayOutputStream(
        declaredLength > 0 && declaredLength < Integer.MAX_VALUE ? (int) declaredLength : BUFFER);
    AtomicLong lastActivity = new AtomicLong(System.nanoTime());
    AtomicBoolean stalled = new AtomicBoolean();
    InputStream body = response.body();
    long period = Math.max(Math.min(properties.getReadTimeoutMs() / 4L, 250L), 10L);
    ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(
        () -> watch(body, deadline, lastActivity, stalled), period, period,
        TimeUnit.MILLISECONDS);
    try (body) {
      int total = 0;
      int count;
      while ((count = body.read(buffer)) != -1) {
        lastActivity.set(System.nanoTime());
        total += count;
        if (total > properties.getMaxBytes()) {
          throw new ResultFetchException("RESULT_TOO_LARGE", "结果文件超过体积上限");
        }
        if (System.nanoTime() > deadline) {
          throw new ResultFetchException("FETCH_TIMEOUT", "结果下载总时长超限");
        }
        out.write(buffer, 0, count);
      }
    } catch (IOException failure) {
      if (stalled.get()) {
        throw new ResultFetchException("FETCH_TIMEOUT", "结果下载读取停滞超时", failure);
      }
      throw new ResultFetchException("FETCH_IO", "结果下载中断", failure);
    } finally {
      watchdog.cancel(false);
    }
    if (stalled.get()) {
      // 看护线程关闭流后读到 EOF 时同样按超时处理，不把截断内容当成完整结果。
      throw new ResultFetchException("FETCH_TIMEOUT", "结果下载读取停滞超时");
    }
    byte[] content = out.toByteArray();
    ImageType type = sniff(content);
    if (type == null) {
      throw new ResultFetchException("RESULT_TYPE_INVALID", "结果文件不是受支持的图片类型");
    }
    // 对象存储常用 octet-stream 表示“未声明类型”，此时以实际文件内容为准。
    if (!declaredType.isEmpty() && !UNDECLARED_TYPE.equals(declaredType)
        && !declaredType.equals(type.contentType())) {
      throw new ResultFetchException("RESULT_TYPE_MISMATCH", "结果文件声明类型与实际不符");
    }
    String digest = sha256(content);
    if (!ProviderResultMetadata.DIGEST_UNDECLARED.equals(expectedDigest)
        && !digest.equalsIgnoreCase(expectedDigest)) {
      throw new ResultFetchException("RESULT_DIGEST_MISMATCH", "结果文件摘要与上游声明不符");
    }
    return new FetchedResult(content, type.contentType(), type.extension(), digest);
  }

  /**
   * 看护正文读取：读取停滞或超过总时限时关闭响应流，阻塞中的 read 随之中断。
   * 关闭是唯一能中断阻塞读的手段；仅由调用方超时返回会留下仍在读取的连接。
   */
  private void watch(InputStream body, long deadline, AtomicLong lastActivity,
      AtomicBoolean stalled) {
    long now = System.nanoTime();
    long idleNanos = TimeUnit.MILLISECONDS.toNanos(properties.getReadTimeoutMs());
    if (now >= deadline || now - lastActivity.get() >= idleNanos) {
      stalled.set(true);
      closeQuietly(body);
    }
  }

  private URI parse(String sourceRef) {
    try {
      URI uri = new URI(sourceRef == null ? "" : sourceRef.trim());
      if (uri.getScheme() == null || uri.getHost() == null
          || !("http".equalsIgnoreCase(uri.getScheme())
              || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getUserInfo() != null || uri.getRawFragment() != null) {
        throw new ResultFetchException("RESULT_REF_INVALID", "结果地址不合法");
      }
      return uri;
    } catch (URISyntaxException invalid) {
      throw new ResultFetchException("RESULT_REF_INVALID", "结果地址不合法");
    }
  }

  private void validateTarget(URI uri) {
    String host = uri.getHost();
    int port = uri.getPort() < 0
        ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
    if (host == null || host.isBlank() || port < 1 || port > 65535) {
      throw new ResultFetchException("RESULT_REF_INVALID", "结果地址不合法");
    }
    boolean privateAllowed = hostAllowed(host, port)
        && (properties.isAllowPrivateHosts() || properties.isSimulatorHost(host, port));
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException unknown) {
      throw new ResultFetchException("RESULT_HOST_UNRESOLVED", "结果地址无法解析");
    }
    if (addresses.length == 0) {
      throw new ResultFetchException("RESULT_HOST_UNRESOLVED", "结果地址无法解析");
    }
    for (InetAddress address : addresses) {
      if (!isPublic(address) && !privateAllowed) {
        throw new ResultFetchException("RESULT_HOST_FORBIDDEN", "结果地址指向受限网络");
      }
    }
  }

  private boolean hostAllowed(String host, int port) {
    if (properties.isSimulatorHost(host, port)) {
      return true;
    }
    String name = host.toLowerCase(Locale.ROOT);
    for (String allowed : properties.getAllowedHosts()) {
      if (allowed.equals(name) || allowed.equals(name + ":" + port)) {
        return true;
      }
    }
    return false;
  }

  private boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress()
        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return false;
    }
    if (address instanceof Inet4Address ipv4) {
      byte[] raw = ipv4.getAddress();
      int first = raw[0] & 0xff;
      int second = raw[1] & 0xff;
      return first != 0 && !(first == 100 && second >= 64 && second <= 127)
          && !(first == 198 && (second == 18 || second == 19))
          && first < 240;
    }
    if (address instanceof Inet6Address ipv6) {
      byte first = ipv6.getAddress()[0];
      // 唯一本地地址 fc00::/7 与未指定地址不属于公网。
      return (first & 0xfe) != 0xfc && !ipv6.isAnyLocalAddress();
    }
    return false;
  }

  private ImageType sniff(byte[] content) {
    if (content.length >= 8 && (content[0] & 0xff) == 0x89
        && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47
        && content[4] == 0x0d && content[5] == 0x0a && content[6] == 0x1a
        && content[7] == 0x0a) {
      return new ImageType("image/png", "png");
    }
    if (content.length >= 3 && (content[0] & 0xff) == 0xff
        && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
      return new ImageType("image/jpeg", "jpg");
    }
    if (content.length >= 12
        && new String(content, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
        && new String(content, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
      return new ImageType("image/webp", "webp");
    }
    return null;
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("缺少 SHA-256 实现", impossible);
    }
  }

  private void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException ignored) {
      // 关闭失败不影响已确定的拉取结论。
    }
  }

  private record ImageType(String contentType, String extension) {
  }
}
