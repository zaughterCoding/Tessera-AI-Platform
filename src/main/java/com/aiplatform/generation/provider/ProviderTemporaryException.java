package com.aiplatform.generation.provider;

/** 查询或提交前暂时不可用；保留原尝试身份与在途预算，等待有界重试。 */
public class ProviderTemporaryException extends RuntimeException {
  private final String code;

  public ProviderTemporaryException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
