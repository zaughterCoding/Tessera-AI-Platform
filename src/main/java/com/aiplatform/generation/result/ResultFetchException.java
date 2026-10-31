package com.aiplatform.generation.result;

/** 拉取失败只表达可重试或需人工处理的原因，不泄露目标内部地址细节。 */
public class ResultFetchException extends RuntimeException {
  private final String code;

  public ResultFetchException(String code, String message) {
    super(message);
    this.code = code;
  }

  public ResultFetchException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
