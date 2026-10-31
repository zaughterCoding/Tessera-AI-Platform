package com.aiplatform.generation.provider;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 回调签名覆盖渠道、时间戳、事件身份与原始请求体，防止跨渠道重放与篡改。 */
public final class ProviderCallbackSignatures {
  private static final String ALGORITHM = "HmacSHA256";

  private ProviderCallbackSignatures() {
  }

  public static byte[] canonical(String channel, long timestamp, String eventKey, byte[] body) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write(out, channel);
    out.write('\n');
    write(out, Long.toString(timestamp));
    out.write('\n');
    write(out, eventKey);
    out.write('\n');
    out.writeBytes(body);
    return out.toByteArray();
  }

  public static String sign(String secret, String channel, long timestamp, String eventKey,
      byte[] body) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(canonical(channel, timestamp, eventKey, body)));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("回调签名不可用", failure);
    }
  }

  public static boolean matches(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    byte[] left = expected.getBytes(StandardCharsets.US_ASCII);
    byte[] right = actual.getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(left, right);
  }

  public static String sha256(byte[] body) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("摘要不可用", failure);
    }
  }

  private static void write(ByteArrayOutputStream out, String value) {
    out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
  }
}
