package com.aiplatform.generation;

import cn.hutool.crypto.digest.DigestUtil;
import com.aiplatform.generation.dto.GenerationTaskForm;
import java.text.Normalizer;
import java.util.List;

/**
 * 请求摘要覆盖所有影响生成承诺的字段。
 *
 * <p>文本先做 Unicode NFC、统一换行并去掉首尾空白，内部空白与大小写保持原样；
 * 字段采用“名称=长度:值”编码，避免拼接歧义。素材顺序参与摘要，重复或非法素材
 * 由后续校验拒绝，不会被静默去重。
 */
public final class GenerationRequestDigest {
  private GenerationRequestDigest() {}

  public static String digest(GenerationTaskForm form) {
    StringBuilder canonical = new StringBuilder("generation-v1\n");
    field(canonical, "applicationId", form.getApplicationId());
    field(canonical, "applicationVersion", form.getApplicationVersion());
    field(canonical, "modelVersion", normalizeToken(form.getModelVersion()));
    field(canonical, "imageSize", normalizeToken(form.getImageSize()));
    field(canonical, "outputCount", form.getOutputCount());
    field(canonical, "prompt", normalizeText(form.getPrompt()));
    field(canonical, "negativePrompt", normalizeText(form.getNegativePrompt()));
    List<Long> mediaIds = form.getMediaIds();
    int count = mediaIds == null ? 0 : mediaIds.size();
    for (int index = 0; index < count; index++) {
      field(canonical, "media" + index, mediaIds.get(index));
    }
    return DigestUtil.sha256Hex(canonical.toString());
  }

  public static String normalizeText(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFC)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .strip();
  }

  public static String normalizeToken(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFC).strip();
  }

  private static void field(StringBuilder canonical, String name, Object value) {
    String text = value == null ? "" : String.valueOf(value);
    canonical.append(name).append('=').append(text.length()).append(':')
        .append(text).append('\n');
  }
}
