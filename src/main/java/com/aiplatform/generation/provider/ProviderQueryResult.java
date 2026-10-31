package com.aiplatform.generation.provider;

/** 查询结果只描述上游事实；结果地址仅作为不可下载的元数据保存。 */
public record ProviderQueryResult(
    QueryStatus status,
    String providerTaskId,
    String resultRef,
    String resultDigest,
    String failureCode,
    String failureDetail) {

  public enum QueryStatus {
    ACCEPTED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    NOT_FOUND
  }

  public static ProviderQueryResult of(QueryStatus status, String providerTaskId) {
    return new ProviderQueryResult(status, providerTaskId, null, null, null, null);
  }
}
