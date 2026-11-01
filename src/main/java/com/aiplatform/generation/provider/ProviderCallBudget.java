package com.aiplatform.generation.provider;

/** 真实调用额度；按渠道持久计数，进程重启与重试都不会绕过本地上限。 */
public interface ProviderCallBudget {
  /** 在发起真实提交前原子占用一次额度；额度用尽或不可判定时返回 false。 */
  boolean tryReserve(String channelCode, int limit);

  /** 当前已使用的真实提交次数；仅用于状态与运维展示。 */
  int used(String channelCode);
}
