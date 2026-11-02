package com.aiplatform.generation.provider;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.config.HttpProviderProperties;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import org.springframework.stereotype.Component;

/**
 * 按尝试所属渠道选择适配器：历史模拟尝试在切换到真实渠道后仍按原渠道完成查询与取消，
 * 不使用当前配置冒充旧尝试的上游身份。适配器本身不含业务状态，替换只影响新提交。
 */
@Component
public class ProviderAdapterRouter implements ProviderChannelAdapter {
  static final String SIMULATED = "SIMULATED";
  static final String HTTP = "HTTP";

  private final SimulatedProviderAdapter simulated;
  private final HttpImageProviderAdapter http;
  private final SiliconFlowImageAdapter siliconFlow;
  private final HttpProviderProperties httpProperties;
  private final ProviderChannelMapper channels;
  private final GenerationProperties properties;

  public ProviderAdapterRouter(SimulatedProviderAdapter simulated,
      HttpImageProviderAdapter http, SiliconFlowImageAdapter siliconFlow,
      HttpProviderProperties httpProperties, ProviderChannelMapper channels,
      GenerationProperties properties) {
    this.simulated = simulated;
    this.http = http;
    this.siliconFlow = siliconFlow;
    this.httpProperties = httpProperties;
    this.channels = channels;
    this.properties = properties;
  }

  @Override
  public String channelCode() {
    return properties.getChannelCode();
  }

  @Override
  public ProviderCapabilities capabilities() {
    ProviderChannel channel = channels.selectByCode(properties.getChannelCode());
    return adapterFor(channel == null ? null : channel.getKind()).capabilities();
  }

  /** 尝试级能力：取消与重提都只按该尝试真实所属的渠道判断。 */
  public ProviderCapabilities capabilitiesFor(String attemptKey) {
    return adapterFor(kindOfAttempt(attemptKey)).capabilities();
  }

  @Override
  public ProviderSubmitResult submit(ProviderSubmitCommand command) {
    return adapterFor(kindOfAttempt(command.attemptKey())).submit(command);
  }

  @Override
  public ProviderQueryResult query(String attemptKey, String providerTaskId) {
    return adapterFor(kindOfAttempt(attemptKey)).query(attemptKey, providerTaskId);
  }

  @Override
  public ProviderCancelResult cancel(String attemptKey, String providerTaskId) {
    return adapterFor(kindOfAttempt(attemptKey)).cancel(attemptKey, providerTaskId);
  }

  /** 新提交前的就绪判定：渠道行启用、适配器已配置，且真实渠道仍有本地额度。 */
  public boolean canSubmit(String channelCode) {
    ProviderChannel channel = channels.selectByCode(channelCode);
    if (channel == null || !"ENABLED".equals(channel.getStatus())) {
      return false;
    }
    if (HTTP.equals(channel.getKind())) {
      return http.isReady();
    }
    return properties.isSimulatorEnabled();
  }

  /** 查询恢复不消耗生成额度；已提交的真实任务即使额度用尽也必须能继续对账。 */
  public boolean canPoll(String attemptKey) {
    String kind = kindOfAttempt(attemptKey);
    if (HTTP.equals(kind)) {
      return http.isConfigured();
    }
    return properties.isSimulatorEnabled();
  }

  private String kindOfAttempt(String attemptKey) {
    String kind = attemptKey == null ? null : channels.selectKindByAttemptKey(attemptKey);
    if (kind != null) {
      return kind;
    }
    ProviderChannel configured = channels.selectByCode(properties.getChannelCode());
    return configured == null ? SIMULATED : configured.getKind();
  }

  private ProviderChannelAdapter adapterFor(String kind) {
    if (!HTTP.equals(kind)) {
      return simulated;
    }
    return HttpProviderProperties.PROTOCOL_SILICONFLOW.equals(httpProperties.getProtocol())
        ? siliconFlow : http;
  }
}
