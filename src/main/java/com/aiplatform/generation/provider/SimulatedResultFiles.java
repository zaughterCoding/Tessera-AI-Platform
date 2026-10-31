package com.aiplatform.generation.provider;

import com.aiplatform.generation.config.ResultDeliveryProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 模拟上游的真实结果文件：按任务与位置确定性生成 PNG，摘要即文件摘要。
 * 只有配置了独立下载地址与密钥时才启用；旧任务的 simulated:// 元数据不会被当作文件。
 */
@Component
public class SimulatedResultFiles {
  private static final int SIZE = 64;
  private static final String PATH_PREFIX = "/internal/simulated/results/";

  private final ResultDeliveryProperties properties;

  public SimulatedResultFiles(ResultDeliveryProperties properties) {
    this.properties = properties;
  }

  public boolean configured() {
    return !properties.getSimulatorResultBaseUrl().isBlank()
        && properties.getDownloadSecret() != null
        && properties.getDownloadSecret().length() >= 32;
  }

  public boolean deliveredUrl(String providerTaskId, String value) {
    return configured() && resultUrl(providerTaskId, 0).equals(value);
  }

  public List<ProviderResultRef> manifest(String providerTaskId, int outputCount) {
    int count = Math.max(1, Math.min(outputCount, 100));
    List<ProviderResultRef> refs = new ArrayList<>(count);
    for (int position = 0; position < count; position++) {
      refs.add(new ProviderResultRef(position, resultUrl(providerTaskId, position),
          digest(png(providerTaskId, position))));
    }
    return List.copyOf(refs);
  }

  public String resultUrl(String providerTaskId, int position) {
    String base = properties.getSimulatorResultBaseUrl();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base + PATH_PREFIX + providerTaskId + "/" + position + "?token="
        + token(providerTaskId, position);
  }

  public String token(String providerTaskId, int position) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(properties.getDownloadSecret()
          .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(
          (providerTaskId + ":" + position).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException failure) {
      throw new IllegalStateException("模拟结果令牌生成失败", failure);
    }
  }

  public boolean tokenMatches(String providerTaskId, int position, String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(token(providerTaskId, position)
            .getBytes(StandardCharsets.US_ASCII),
        candidate.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
  }

  public String digest(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("缺少 SHA-256 实现", impossible);
    }
  }

  /** 生成合法的 64×64 RGB PNG；颜色与纹理完全由任务键和位置决定。 */
  public byte[] png(String providerTaskId, int position) {
    byte[] seed = digest((providerTaskId + ":" + position)
        .getBytes(StandardCharsets.UTF_8)).getBytes(StandardCharsets.US_ASCII);
    byte[] raw = new byte[SIZE * (SIZE * 3 + 1)];
    int cursor = 0;
    for (int y = 0; y < SIZE; y++) {
      raw[cursor++] = 0;
      for (int x = 0; x < SIZE; x++) {
        int offset = (y * SIZE + x) % seed.length;
        raw[cursor++] = seed[offset];
        raw[cursor++] = seed[(offset + 7) % seed.length];
        raw[cursor++] = seed[(offset + 13) % seed.length];
      }
    }
    byte[] idat = deflate(raw);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    png.writeBytes(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
    byte[] header = new byte[13];
    putInt(header, 0, SIZE);
    putInt(header, 4, SIZE);
    header[8] = 8;
    header[9] = 2;
    chunk(png, "IHDR", header);
    chunk(png, "IDAT", idat);
    chunk(png, "IEND", new byte[0]);
    return png.toByteArray();
  }

  private byte[] deflate(byte[] raw) {
    Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
    try {
      deflater.setInput(raw);
      deflater.finish();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      while (!deflater.finished()) {
        int count = deflater.deflate(buffer);
        out.write(buffer, 0, count);
      }
      return out.toByteArray();
    } finally {
      deflater.end();
    }
  }

  private void chunk(ByteArrayOutputStream out, String type, byte[] data) {
    byte[] name = type.getBytes(StandardCharsets.US_ASCII);
    byte[] length = new byte[4];
    putInt(length, 0, data.length);
    out.writeBytes(length);
    out.writeBytes(name);
    out.writeBytes(data);
    CRC32 crc = new CRC32();
    crc.update(name);
    crc.update(data);
    byte[] checksum = new byte[4];
    putInt(checksum, 0, (int) crc.getValue());
    out.writeBytes(checksum);
  }

  private void putInt(byte[] target, int offset, int value) {
    target[offset] = (byte) (value >>> 24);
    target[offset + 1] = (byte) (value >>> 16);
    target[offset + 2] = (byte) (value >>> 8);
    target[offset + 3] = (byte) value;
  }
}
