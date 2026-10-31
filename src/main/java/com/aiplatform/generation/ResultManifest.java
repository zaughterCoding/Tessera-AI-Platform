package com.aiplatform.generation;

import com.aiplatform.generation.provider.ProviderResultRef;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 上游结果清单的结构校验；只有位置完整且摘要合法的清单才能进入交付流程。 */
public final class ResultManifest {
  public static final int MAX_RESULTS = 100;
  public static final int MAX_REF_LENGTH = 512;
  private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern POSITION = Pattern.compile("\\d{1,2}");

  private ResultManifest() {
  }

  /** 返回按位置排序的副本；任何缺失、重复或非法项都整体拒绝，避免“部分成功”。 */
  public static List<ProviderResultRef> complete(List<ProviderResultRef> results,
      int outputCount) {
    if (outputCount <= 0 || outputCount > MAX_RESULTS || results == null
        || results.size() != outputCount) {
      return null;
    }
    List<ProviderResultRef> ordered = new ArrayList<>(results.size());
    Set<Integer> positions = new HashSet<>();
    for (ProviderResultRef item : results) {
      if (item == null || item.position() < 0 || item.position() >= outputCount
          || !positions.add(item.position())
          || !validRef(item.ref()) || item.digest() == null
          || !DIGEST.matcher(item.digest()).matches()) {
        return null;
      }
      ordered.add(new ProviderResultRef(item.position(), item.ref().trim(), item.digest()));
    }
    ordered.sort(Comparator.comparingInt(ProviderResultRef::position));
    return List.copyOf(ordered);
  }

  /** 兼容 N03 的单结果字段；仅当任务只产出一张图时才可沿用。 */
  public static List<ProviderResultRef> legacy(String ref, String digest, int outputCount) {
    if (outputCount != 1) {
      return null;
    }
    return complete(List.of(new ProviderResultRef(0, ref, digest)), 1);
  }

  /** 回调验签后先做与任务无关的格式校验；任务输出数量在事务内复核。 */
  public static boolean structurallyValid(List<ProviderResultRef> results) {
    if (results == null || results.isEmpty() || results.size() > MAX_RESULTS) {
      return false;
    }
    Set<Integer> positions = new HashSet<>();
    for (ProviderResultRef item : results) {
      if (item == null || item.position() < 0 || item.position() >= MAX_RESULTS
          || !positions.add(item.position())
          || !validRef(item.ref()) || item.digest() == null
          || !DIGEST.matcher(item.digest()).matches()) {
        return false;
      }
    }
    return true;
  }

  private static boolean validRef(String ref) {
    if (ref == null || ref.isBlank() || ref.length() > MAX_REF_LENGTH) {
      return false;
    }
    try {
      URI uri = new URI(ref.trim());
      return ("http".equalsIgnoreCase(uri.getScheme())
          || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null && uri.getUserInfo() == null && uri.getRawFragment() == null;
    } catch (URISyntaxException invalid) {
      return false;
    }
  }

  public static boolean validPosition(String value) {
    return value != null && POSITION.matcher(value).matches();
  }
}
