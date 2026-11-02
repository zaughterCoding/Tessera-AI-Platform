package com.aiplatform.generation.dto;

import com.aiplatform.generation.entity.ProviderChannel;
import java.time.LocalDateTime;

/** 管理视图中的渠道容量与能力；不含任何密钥或上游地址。 */
public record GenerationChannelDTO(
    long id,
    String code,
    String displayName,
    String kind,
    String status,
    int maxInFlight,
    int inFlight,
    Integer effectiveInFlight,
    LocalDateTime dispatchPausedUntil,
    int queueCapacity,
    int queued,
    int queueTimeoutSeconds,
    boolean supportsIdempotentSubmit,
    boolean supportsQueryByAttempt,
    boolean supportsCancel) {

  public static GenerationChannelDTO from(ProviderChannel channel) {
    return new GenerationChannelDTO(channel.getId(), channel.getCode(),
        channel.getDisplayName(), channel.getKind(), channel.getStatus(),
        value(channel.getMaxInFlight()), value(channel.getInFlight()),
        channel.getEffectiveInFlight(), channel.getDispatchPausedUntil(),
        value(channel.getQueueCapacity()), value(channel.getQueued()),
        value(channel.getQueueTimeoutSeconds()),
        Boolean.TRUE.equals(channel.getSupportsIdempotentSubmit()),
        Boolean.TRUE.equals(channel.getSupportsQueryByAttempt()),
        Boolean.TRUE.equals(channel.getSupportsCancel()));
  }

  private static int value(Integer number) {
    return number == null ? 0 : number;
  }
}
