package com.aiplatform.generation.provider;

/** 请求可能已经到达上游但没有可信响应；只能查询原尝试身份，不能盲目新建尝试。 */
public class ProviderUncertainException extends RuntimeException {
  private final String code;

  public ProviderUncertainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
