package com.aiplatform.generation.provider;

/** 上游渠道适配契约；网络调用不得发生在持有业务行锁的事务内。 */
public interface ProviderChannelAdapter {
  String channelCode();

  ProviderCapabilities capabilities();

  ProviderSubmitResult submit(ProviderSubmitCommand command);

  ProviderQueryResult query(String attemptKey, String providerTaskId);

  ProviderCancelResult cancel(String attemptKey, String providerTaskId);
}
