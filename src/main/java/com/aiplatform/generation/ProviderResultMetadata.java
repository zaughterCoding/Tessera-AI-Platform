package com.aiplatform.generation;

import com.aiplatform.generation.entity.ProviderAttempt;
import java.util.regex.Pattern;

/** 成功终态必须同时具备结果引用与摘要；缺失即视为事实不完整，不能按已记录元数据对待。 */
public final class ProviderResultMetadata {
  private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

  private ProviderResultMetadata() {
  }

  public static boolean isComplete(String resultRef, String resultDigest) {
    return resultRef != null && !resultRef.isBlank()
        && resultDigest != null && DIGEST.matcher(resultDigest).matches();
  }

  public static boolean isMissing(ProviderAttempt attempt) {
    return attempt != null && !isComplete(attempt.getResultRef(), attempt.getResultDigest());
  }

  public static boolean successMetadataMissing(String taskState, ProviderAttempt attempt) {
    return GenerationTaskState.RESULT_PENDING.equals(taskState) && isMissing(attempt);
  }
}
