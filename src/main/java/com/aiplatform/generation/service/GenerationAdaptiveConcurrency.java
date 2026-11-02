package com.aiplatform.generation.service;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * AIMD：确实跑满且没有被限流时每个观察窗口加一，收到 429 时减半并暂停发送。
 * 状态都在渠道行上，判断都是条件更新，重启后继续生效；只看 429，超时和 5xx 不参与调整。
 */
@Component
public class GenerationAdaptiveConcurrency {
  private final ProviderChannelMapper channels;
  private final GenerationProperties properties;
  private final AtomicInteger effective = new AtomicInteger();
  private final Counter throttled;

  public GenerationAdaptiveConcurrency(ProviderChannelMapper channels,
      GenerationProperties properties, MeterRegistry metrics) {
    this.channels = channels;
    this.properties = properties;
    this.throttled = metrics.counter("aiplatform.generation.provider.throttled");
    metrics.gauge("aiplatform.generation.effective.in.flight", effective);
  }

  public boolean enabled() {
    return properties.isAdaptiveConcurrencyEnabled();
  }

  /** 本轮可用的在途上限；开关关闭时使用固定的 max_in_flight。 */
  public int limit(ProviderChannel channel) {
    int limit = channel.getMaxInFlight();
    if (enabled() && channel.getEffectiveInFlight() != null) {
      limit = Math.min(limit, channel.getEffectiveInFlight());
    }
    effective.set(limit);
    return limit;
  }

  public boolean paused(long channelId) {
    return enabled() && channels.countDispatchPaused(channelId) > 0;
  }

  /** 空位全部用完且还有排队才算跑满；否则窗口重新计时，生效值不会无意义地上涨。 */
  public void afterClaim(ProviderChannel channel, int slots, int claimed) {
    if (!enabled()) {
      return;
    }
    if (claimed >= slots && channel.getQueued() > claimed) {
      channels.growEffectiveInFlight(channel.getId(), properties.getAdaptiveWindowSeconds());
    } else {
      channels.resetAdaptiveWindow(channel.getId());
    }
  }

  /** 暂停期内不重复减半：同一批提交连续收到多个 429 只算一次拥塞。 */
  public void onThrottled(long channelId, Integer retryAfterSeconds) {
    throttled.increment();
    if (!enabled()) {
      return;
    }
    int pause = retryAfterSeconds == null ? properties.getAdaptivePauseSeconds()
        : Math.max(1, Math.min(retryAfterSeconds, properties.getRetryMaxSeconds()));
    channels.halveEffectiveInFlight(channelId, pause);
  }
}
