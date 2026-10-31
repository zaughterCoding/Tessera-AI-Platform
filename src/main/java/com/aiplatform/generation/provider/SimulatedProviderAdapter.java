package com.aiplatform.generation.provider;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.entity.SimulatedProviderJob;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/** 唯一可用的模拟上游；能力差异来自渠道行，不以假成功绕过渠道契约。 */
@Component
public class SimulatedProviderAdapter implements ProviderChannelAdapter {
  private final SimulatedProviderStore store;
  private final SimulatedProviderCallbackSender callbacks;
  private final ProviderChannelMapper channels;
  private final GenerationProperties properties;
  private final SimulatedResultFiles resultFiles;

  public SimulatedProviderAdapter(SimulatedProviderStore store,
      SimulatedProviderCallbackSender callbacks, ProviderChannelMapper channels,
      GenerationProperties properties, SimulatedResultFiles resultFiles) {
    this.store = store;
    this.callbacks = callbacks;
    this.channels = channels;
    this.properties = properties;
    this.resultFiles = resultFiles;
  }

  @Override
  public String channelCode() {
    return properties.getChannelCode();
  }

  @Override
  public ProviderCapabilities capabilities() {
    ProviderChannel channel = channels.selectByCode(properties.getChannelCode());
    if (channel == null) {
      return new ProviderCapabilities(false, false, false);
    }
    return new ProviderCapabilities(Boolean.TRUE.equals(channel.getSupportsIdempotentSubmit()),
        Boolean.TRUE.equals(channel.getSupportsQueryByAttempt()),
        Boolean.TRUE.equals(channel.getSupportsCancel()));
  }

  @Override
  public ProviderSubmitResult submit(ProviderSubmitCommand command) {
    requireEnabled();
    ProviderCapabilities capabilities = capabilities();
    if (!capabilities.idempotentSubmit() || !capabilities.queryByAttempt()) {
      throw new IllegalStateException("模拟渠道缺少幂等提交或按尝试查询能力");
    }
    SimulatedProviderStore.Acceptance acceptance = store.accept(command.attemptKey(),
        command.userId(), command.applicationId(), command.outputCount());
    if (acceptance.rejected()) {
      throw new ProviderRejectedException("SIMULATED_REJECTED", "模拟上游明确拒收");
    }
    if (acceptance.job() == null) {
      throw new ProviderTemporaryException("SIMULATED_UNAVAILABLE", "模拟上游未建立任务");
    }
    if (acceptance.responseUncertain()) {
      throw new ProviderUncertainException(acceptance.mode(), "模拟上游已受理但响应不可信");
    }
    if (!"RUNNING".equals(acceptance.job().getState())) {
      // 幂等键已对应终态任务时不能冒充新受理；交给查询对账收敛。
      throw new ProviderUncertainException("SIMULATED_ALREADY_TERMINAL", "上游任务已处于终态");
    }
    return new ProviderSubmitResult(acceptance.job().getState(),
        acceptance.job().getProviderTaskId());
  }

  @Override
  public ProviderQueryResult query(String attemptKey, String providerTaskId) {
    requireEnabled();
    SimulatedProviderStore.QueryOutcome outcome = store.query(attemptKey);
    if (outcome.kind() == SimulatedProviderStore.QueryOutcome.Kind.NOT_FOUND) {
      return ProviderQueryResult.of(ProviderQueryResult.QueryStatus.NOT_FOUND, providerTaskId);
    }
    if (outcome.kind() == SimulatedProviderStore.QueryOutcome.Kind.TEMPORARY) {
      throw new ProviderTemporaryException("SIMULATED_QUERY_FAILURE", "模拟查询暂时不可用");
    }
    SimulatedProviderJob job = outcome.job();
    ProviderQueryResult.QueryStatus status = switch (job.getState()) {
      case "RUNNING" -> ProviderQueryResult.QueryStatus.RUNNING;
      case "SUCCEEDED" -> ProviderQueryResult.QueryStatus.SUCCEEDED;
      case "FAILED" -> ProviderQueryResult.QueryStatus.FAILED;
      case "CANCELLED" -> ProviderQueryResult.QueryStatus.CANCELLED;
      default -> ProviderQueryResult.QueryStatus.RUNNING;
    };
    List<ProviderResultRef> results = null;
    if (status == ProviderQueryResult.QueryStatus.SUCCEEDED
        && resultFiles.deliveredUrl(job.getProviderTaskId(), job.getResultRef())) {
      results = resultFiles.manifest(job.getProviderTaskId(), outputCount(job));
    }
    if (status == ProviderQueryResult.QueryStatus.SUCCEEDED
        || status == ProviderQueryResult.QueryStatus.FAILED
        || status == ProviderQueryResult.QueryStatus.CANCELLED) {
      callbacks.notifyTerminal(attemptKey, job.getProviderTaskId(), job.getState(),
          job.getResultRef(), job.getResultDigest(), job.getFailureCode(),
          job.getFailureDetail(), results);
    }
    return new ProviderQueryResult(status, job.getProviderTaskId(), job.getResultRef(),
        job.getResultDigest(), job.getFailureCode(), job.getFailureDetail(), results);
  }

  private int outputCount(SimulatedProviderJob job) {
    return job.getOutputCount() == null || job.getOutputCount() < 1
        ? 1 : job.getOutputCount();
  }

  @Override
  public ProviderCancelResult cancel(String attemptKey, String providerTaskId) {
    if (!capabilities().cancel()) {
      return new ProviderCancelResult(ProviderCancelResult.CancelStatus.UNSUPPORTED, null);
    }
    requireEnabled();
    SimulatedProviderStore.CancelOutcome outcome = store.cancel(attemptKey);
    return new ProviderCancelResult(outcome.status(), outcome.providerState());
  }

  private void requireEnabled() {
    if (!properties.isSimulatorEnabled()) {
      throw new ProviderTemporaryException("SIMULATOR_DISABLED", "模拟渠道当前未启用");
    }
  }
}
