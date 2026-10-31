package com.aiplatform.generation.provider;

import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.entity.SimulatedProviderFault;
import com.aiplatform.generation.entity.SimulatedProviderJob;
import com.aiplatform.generation.mapper.SimulatedProviderMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 模拟上游写入使用独立事务；业务事务回滚不会撤销上游事实，反之亦然。 */
@Service
public class SimulatedProviderStore {
  private static final String NONE = "NONE";
  private static final String REJECT = "REJECT_SUBMIT";
  private static final String LOSE_RESPONSE = "LOSE_SUBMIT_RESPONSE";
  private static final String TIMEOUT_RUNNING = "TIMEOUT_BUT_RUNNING";
  private static final String SLOW = "SLOW";
  private static final String FAIL = "FAIL";
  private static final String QUERY_FAILURE = "QUERY_TEMPORARY_FAILURE";

  private final SimulatedProviderMapper mapper;
  private final GenerationProperties properties;
  private final SimulatedResultFiles resultFiles;

  public SimulatedProviderStore(SimulatedProviderMapper mapper, GenerationProperties properties,
      SimulatedResultFiles resultFiles) {
    this.mapper = mapper;
    this.properties = properties;
    this.resultFiles = resultFiles;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Acceptance accept(String attemptKey, Long userId, Long applicationId, int outputCount) {
    SimulatedProviderFault fault = mapper.selectFaultForUpdate(attemptKey, userId, applicationId);
    String mode = fault == null ? NONE : fault.getMode();
    if (fault != null) {
      consume(fault);
    }
    if (REJECT.equals(mode)) {
      return new Acceptance(null, mode);
    }
    SimulatedProviderJob existing = mapper.selectJob(attemptKey);
    if (existing == null) {
      String providerTaskId = "sim-" + UUID.randomUUID();
      int duration = duration(mode, fault);
      int queryFailures = QUERY_FAILURE.equals(mode) ? properties.getSimulatorQueryFailures() : 0;
      // 幂等键竞争时由唯一键吸收；仍以数据库中的既有任务为唯一上游事实。
      mapper.insertJobIgnore(attemptKey, providerTaskId, "RUNNING", mode, duration,
          Math.max(1, outputCount), queryFailures);
      existing = mapper.selectJob(attemptKey);
    }
    return new Acceptance(existing, mode);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public QueryOutcome query(String attemptKey) {
    SimulatedProviderJob job = mapper.selectJobForUpdate(attemptKey);
    if (job == null) {
      return QueryOutcome.notFound();
    }
    if ("RUNNING".equals(job.getState()) && value(job.getQueryFailuresRemaining()) > 0) {
      mapper.decrementQueryFailures(job.getId());
      return QueryOutcome.temporary();
    }
    if ("RUNNING".equals(job.getState())) {
      String target = FAIL.equals(job.getFaultMode()) ? "FAILED" : "SUCCEEDED";
      boolean realFiles = "SUCCEEDED".equals(target) && resultFiles.configured();
      // 新任务写入可下载的真实文件引用；旧任务保留 simulated:// 元数据，绝不伪造文件。
      String resultRef = "SUCCEEDED".equals(target)
          ? (realFiles ? resultFiles.resultUrl(job.getProviderTaskId(), 0)
              : "simulated://result/" + job.getProviderTaskId()) : null;
      byte[] digestSource = (job.getProviderTaskId() + ":SUCCEEDED")
          .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      String resultDigest = "SUCCEEDED".equals(target)
          ? (realFiles ? resultFiles.digest(resultFiles.png(job.getProviderTaskId(), 0))
              : ProviderCallbackSignatures.sha256(digestSource)) : null;
      String failureCode = "FAILED".equals(target) ? "SIMULATED_FAILURE" : null;
      String failureDetail = "FAILED".equals(target) ? "模拟上游返回失败" : null;
      if (mapper.completeJob(job.getId(), target, resultRef, resultDigest,
          failureCode, failureDetail) == 1) {
        job = mapper.selectJob(attemptKey);
      }
    }
    return QueryOutcome.of(job);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CancelOutcome cancel(String attemptKey) {
    SimulatedProviderJob job = mapper.selectJobForUpdate(attemptKey);
    if (job == null) {
      return new CancelOutcome(ProviderCancelResult.CancelStatus.NOT_FOUND, null);
    }
    if ("RUNNING".equals(job.getState())) {
      mapper.cancelJob(job.getId());
      job = mapper.selectJob(attemptKey);
      return new CancelOutcome(ProviderCancelResult.CancelStatus.CANCELLED, job.getState());
    }
    return new CancelOutcome(ProviderCancelResult.CancelStatus.TERMINAL, job.getState());
  }

  private void consume(SimulatedProviderFault fault) {
    if (value(fault.getRemaining()) <= 1) {
      mapper.deleteFault(fault.getId());
    } else {
      mapper.decrementFault(fault.getId());
    }
  }

  private int duration(String mode, SimulatedProviderFault fault) {
    if (fault != null && fault.getDurationMs() != null && fault.getDurationMs() > 0) {
      return fault.getDurationMs();
    }
    if (TIMEOUT_RUNNING.equals(mode) || SLOW.equals(mode)) {
      return Math.max(properties.getSimulatorDefaultDurationMs() * 10, 30000);
    }
    return properties.getSimulatorDefaultDurationMs();
  }

  private int value(Integer number) {
    return number == null ? 0 : number;
  }

  public record Acceptance(SimulatedProviderJob job, String mode) {
    public boolean rejected() {
      return REJECT.equals(mode);
    }

    public boolean responseUncertain() {
      return LOSE_RESPONSE.equals(mode) || TIMEOUT_RUNNING.equals(mode);
    }
  }

  public record QueryOutcome(Kind kind, SimulatedProviderJob job) {
    public enum Kind {
      FOUND,
      NOT_FOUND,
      TEMPORARY
    }

    public static QueryOutcome notFound() {
      return new QueryOutcome(Kind.NOT_FOUND, null);
    }

    public static QueryOutcome temporary() {
      return new QueryOutcome(Kind.TEMPORARY, null);
    }

    public static QueryOutcome of(SimulatedProviderJob job) {
      return new QueryOutcome(Kind.FOUND, job);
    }
  }

  public record CancelOutcome(ProviderCancelResult.CancelStatus status, String providerState) {
  }
}
