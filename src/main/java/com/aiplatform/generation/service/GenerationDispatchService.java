package com.aiplatform.generation.service;

import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.ProviderResultMetadata;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.GenerationHandoffMapper;
import com.aiplatform.generation.mapper.GenerationHandoffMapper.HandoffRow;
import com.aiplatform.generation.mapper.GenerationQuotaMapper;
import com.aiplatform.generation.mapper.GenerationResultMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import com.aiplatform.generation.provider.ProviderAdapterRouter;
import com.aiplatform.generation.provider.ProviderCapabilities;
import com.aiplatform.generation.provider.ProviderQueryResult;
import com.aiplatform.generation.provider.ProviderRejectedException;
import com.aiplatform.generation.provider.ProviderSubmitResult;
import com.aiplatform.generation.provider.ProviderTemporaryException;
import com.aiplatform.generation.provider.ProviderUncertainException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 认领与写回使用短事务；网络提交和查询只在事务提交后执行。 */
@Slf4j
@Service
public class GenerationDispatchService {
  private final GenerationHandoffMapper handoffs;
  private final ProviderAttemptMapper attempts;
  private final ProviderChannelMapper channels;
  private final GenerationTaskMapper tasks;
  private final GenerationQuotaMapper quotas;
  private final ProviderAdapterRouter adapter;
  private final GenerationUpstreamTransactions upstream;
  private final GenerationSettlementTransactions settlements;
  private final GenerationResultMapper results;
  private final GenerationProperties properties;

  public GenerationDispatchService(GenerationHandoffMapper handoffs,
      ProviderAttemptMapper attempts, ProviderChannelMapper channels, GenerationTaskMapper tasks,
      GenerationQuotaMapper quotas, ProviderAdapterRouter adapter,
      GenerationUpstreamTransactions upstream, GenerationSettlementTransactions settlements,
      GenerationResultMapper results, GenerationProperties properties) {
    this.handoffs = handoffs;
    this.attempts = attempts;
    this.channels = channels;
    this.tasks = tasks;
    this.quotas = quotas;
    this.adapter = adapter;
    this.upstream = upstream;
    this.settlements = settlements;
    this.results = results;
    this.properties = properties;
  }

  /** 只返回已持久化的尝试；调用方随后才能进行网络提交。 */
  @Transactional
  public List<AttemptClaim> claimDueHandoffs() {
    if (!properties.isSchedulingEnabled()) {
      return Collections.emptyList();
    }
    ProviderChannel channel = channels.selectByCode(properties.getChannelCode());
    if (channel == null) {
      log.error("生成调度渠道不存在，channel={}", properties.getChannelCode());
      return Collections.emptyList();
    }
    if (!"ENABLED".equals(channel.getStatus())) {
      return Collections.emptyList();
    }
    if (!adapter.canSubmit(channel.getCode())) {
      // 渠道关闭或真实渠道未配置时只等待，不认领、不创建尝试，也不改变冻结事实。
      return Collections.emptyList();
    }
    List<HandoffRow> rows = handoffs.selectDueHandoffs(properties.getDispatchBatch());
    List<AttemptClaim> claims = new ArrayList<>();
    for (HandoffRow row : rows) {
      if ("CLAIMED".equals(row.getHandoffState())) {
        recoverExpiredClaim(row);
        continue;
      }
      if (!GenerationTaskState.QUEUED.equals(row.getTaskState())) {
        if (handoffs.completeWaitingHandoff(row.getHandoffId(), row.getHandoffState(),
            "TASK_" + row.getTaskState()) == 1) {
          channels.releaseQueue(channel.getId());
        }
        continue;
      }
      LocalDateTime deadline = row.getEnqueuedAt()
          .plusSeconds(channel.getQueueTimeoutSeconds());
      if (!deadline.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
        timeoutQueued(row, channel);
        continue;
      }
      AttemptClaim claim = claimOne(row, channel);
      if (claim != null) {
        claims.add(claim);
      }
    }
    return claims;
  }

  private AttemptClaim claimOne(HandoffRow row, ProviderChannel channel) {
    String owner = UUID.randomUUID().toString();
    if (handoffs.claimHandoff(row.getHandoffId(), owner,
        properties.getLeaseSeconds()) != 1) {
      return null;
    }
    if (channels.occupyInFlight(channel.getId()) != 1) {
      // 容量不足只是等待，不是失败；改为稍后重试，不创建尝试、不释放冻结。
      handoffs.markRetry(row.getHandoffId(), "CLAIMED", 2, "CAPACITY_WAIT");
      return null;
    }
    ProviderAttempt existing = attempts.selectByTaskId(row.getTaskId());
    if (existing != null) {
      // 防御性分支：责任已有尝试，归还本次占用的上游预算，队列占用保持由既有尝试承担。
      channels.releaseInFlight(channel.getId());
      handoffs.completeClaimedHandoff(row.getHandoffId(), row.getGeneration() + 1,
          "ATTEMPT_EXISTING");
      return null;
    }
    // 先抢占任务状态再创建尝试：用户取消若已把任务改为 CANCEL_REQUESTED，本次认领作废。
    if (tasks.updateStateIn(row.getTaskId(), GenerationTaskState.DISPATCHING,
        GenerationFactApplier.noteFor(ProviderAttemptState.SUBMITTING),
        List.of(GenerationTaskState.QUEUED)) != 1) {
      channels.releaseInFlight(channel.getId());
      handoffs.completeClaimedHandoff(row.getHandoffId(), row.getGeneration() + 1,
          "TASK_STATE_CHANGED");
      return null;
    }
    ProviderAttempt attempt = new ProviderAttempt();
    attempt.setTaskId(row.getTaskId());
    attempt.setChannelId(channel.getId());
    attempt.setAttemptKey(UUID.randomUUID().toString());
    attempt.setState(ProviderAttemptState.SUBMITTING);
    attempt.setOwner(owner);
    attempt.setGeneration(0L);
    if (attempts.insertAttempt(attempt, properties.getLeaseSeconds()) != 1) {
      throw new IllegalStateException("稳定尝试写入失败");
    }
    GenerationTask task = tasks.selectById(row.getTaskId());
    if (task == null) {
      throw new IllegalStateException("已受理任务不存在");
    }
    return claim(attempt, task, row.getHandoffId(), row.getGeneration() + 1);
  }

  private void timeoutQueued(HandoffRow row, ProviderChannel channel) {
    // 交接行完成是唯一的排队责任闸门：取消与超时只有一个能释放队列与用户名额。
    if (handoffs.completeWaitingHandoff(row.getHandoffId(), row.getHandoffState(),
        "QUEUE_TIMEOUT") != 1) {
      return;
    }
    channels.releaseQueue(channel.getId());
    quotas.release(row.getUserId());
    settlements.release(row.getTaskId(), GenerationTaskState.QUEUE_TIMEOUT,
        GenerationFactApplier.terminalNote(GenerationTaskState.QUEUE_TIMEOUT),
        List.of(GenerationTaskState.QUEUED));
  }

  private void recoverExpiredClaim(HandoffRow row) {
    ProviderAttempt attempt = attempts.selectByTaskId(row.getTaskId());
    handoffs.clearExpiredClaim(row.getHandoffId(), row.getGeneration());
    if (attempt != null) {
      // 尝试已持久化，责任转交查询恢复；交接行不再参与调度。
      // 只接管仍停留在 DISPATCHING 的任务，避免覆盖取消请求或已推进的执行状态。
      handoffs.completeClaimedHandoff(row.getHandoffId(), row.getGeneration(),
          "LEASE_RECOVERED");
      tasks.updateStateIn(row.getTaskId(), GenerationTaskState.RECONCILING,
          GenerationFactApplier.noteFor(ProviderAttemptState.RECONCILING),
          List.of(GenerationTaskState.DISPATCHING));
    } else if (!GenerationTaskState.QUEUED.equals(row.getTaskState())) {
      // 任务已被取消等状态接管：本次认领作废，队列名额已由占用动作消耗，不再重复归还。
      handoffs.completeClaimedHandoff(row.getHandoffId(), row.getGeneration(),
          "TASK_STATE_CHANGED");
    } else {
      handoffs.markRetry(row.getHandoffId(), "CLAIMED", 2, "CLAIM_LEASE_EXPIRED");
    }
  }

  /** 轮询认领同样只做短事务，查询在提交后执行。 */
  @Transactional
  public List<AttemptClaim> claimDuePolls() {
    if (!properties.isPollingEnabled()) {
      return Collections.emptyList();
    }
    List<ProviderAttempt> rows = attempts.selectDueForPoll(properties.getPollBatch());
    List<AttemptClaim> claims = new ArrayList<>();
    for (ProviderAttempt row : rows) {
      if (!adapter.canPoll(row.getAttemptKey())) {
        // 该尝试所属渠道当前不可查询：跳过并保持既有冻结与租约，不猜测终态。
        continue;
      }
      String owner = UUID.randomUUID().toString();
      if (attempts.claimForPoll(row.getId(), owner, properties.getLeaseSeconds()) != 1) {
        continue;
      }
      ProviderAttempt fresh = attempts.selectById(row.getId());
      GenerationTask task = tasks.selectById(fresh.getTaskId());
      if (task == null) {
        continue;
      }
      // 终态任务仍可能需要补齐历史成功快照缺失的结果元数据或完整结果清单。
      int outputCount = task.getOutputCount() == null ? 0 : task.getOutputCount();
      boolean repairable = ProviderAttemptState.RESULT_PENDING.equals(fresh.getState())
          && (ProviderResultMetadata.isMissing(fresh)
              || results.countByTaskId(task.getId()) < outputCount);
      if (GenerationTaskState.isUpstreamFinal(task.getState()) && !repairable) {
        continue;
      }
      claims.add(claim(fresh, task, 0, 0));
    }
    return claims;
  }

  public void submitClaim(AttemptClaim claim) {
    if (!adapter.canSubmit(properties.getChannelCode())) {
      upstream.markUncertain(claim, "CHANNEL_NOT_READY", "生成渠道未启用或未配置");
      return;
    }
    try {
      ProviderSubmitResult result = adapter.submit(claim.command());
      upstream.confirmSubmit(claim, result);
    } catch (ProviderRejectedException rejected) {
      upstream.rejectSubmit(claim, rejected.code(), rejected.getMessage());
    } catch (ProviderUncertainException uncertain) {
      upstream.markUncertain(claim, uncertain.code(), uncertain.getMessage());
    } catch (ProviderTemporaryException temporary) {
      upstream.markUncertain(claim, temporary.code(), temporary.getMessage());
    } catch (RuntimeException failure) {
      // 未知提交异常一律按不确定处理，绝不猜测上游失败或自动退款。
      upstream.markUncertain(claim, "SUBMIT_UNKNOWN", failure.getClass().getSimpleName());
    }
  }

  public void pollClaim(AttemptClaim claim) {
    if (!properties.isPollingEnabled() || !adapter.canPoll(claim.attemptKey())) {
      return;
    }
    try {
      ProviderQueryResult result = adapter.query(claim.attemptKey(), claim.providerTaskId());
      if (ProviderAttemptState.RESULT_PENDING.equals(claim.state())) {
        // 既有成功终态只允许补全元数据或延后补查，任何查询结果都不能逆转或重复释放。
        upstream.terminal(claim, result);
        return;
      }
      switch (result.status()) {
        case ACCEPTED, RUNNING -> upstream.active(claim, result);
        case SUCCEEDED, FAILED, CANCELLED -> upstream.terminal(claim, result);
        case NOT_FOUND -> handleNotFound(claim);
        default -> upstream.review(claim, "QUERY_UNKNOWN", "未知查询结果");
      }
    } catch (ProviderTemporaryException temporary) {
      queryFailure(claim, temporary.code(), temporary.getMessage());
    } catch (RuntimeException failure) {
      queryFailure(claim, "QUERY_UNKNOWN", failure.getClass().getSimpleName());
    }
  }

  private void handleNotFound(AttemptClaim claim) {
    if (ProviderAttemptState.RESULT_PENDING.equals(claim.state())) {
      // 终态已成立却不能重提；只延后补查，保留冻结与既有容量事实。
      upstream.delayResultRepair(claim, "UPSTREAM_RESULT_NOT_FOUND", "查询不到原任务");
      return;
    }
    ProviderCapabilities capabilities = adapter.capabilitiesFor(claim.attemptKey());
    if (!capabilities.idempotentSubmit() || !capabilities.queryByAttempt()) {
      upstream.review(claim, "CAPABILITY_UNCERTAIN", "渠道缺少幂等或按尝试查询能力");
      return;
    }
    if (ProviderAttemptState.ACCEPTED.equals(claim.state())
        || ProviderAttemptState.RUNNING.equals(claim.state())) {
      upstream.review(claim, "UPSTREAM_TASK_NOT_FOUND", "上游已受理但查询不到原任务");
      return;
    }
    if (claim.submitAttempts() >= properties.getSubmitMaxAttempts()) {
      upstream.review(claim, "SUBMIT_RETRY_EXHAUSTED", "查询不到任务且提交次数已用尽");
      return;
    }
    try {
      // 复用同一 attemptKey 安全重提；即使上游实际已受理，也不会产生第二个上游任务。
      ProviderSubmitResult result = adapter.submit(claim.command());
      upstream.resubmit(claim, result);
    } catch (ProviderRejectedException rejected) {
      upstream.rejectSubmit(claim, rejected.code(), rejected.getMessage());
    } catch (RuntimeException failure) {
      queryFailure(claim, "RESUBMIT_UNCERTAIN", failure.getClass().getSimpleName());
    }
  }

  private void queryFailure(AttemptClaim claim, String code, String detail) {
    if (ProviderAttemptState.RESULT_PENDING.equals(claim.state())) {
      upstream.delayResultRepair(claim, code, detail == null ? "查询失败" : detail);
      return;
    }
    boolean exhausted = claim.queryAttempts() + 1 >= properties.getQueryMaxAttempts();
    upstream.queryFailure(claim, code, detail == null ? "查询失败" : detail, exhausted);
  }

  private AttemptClaim claim(ProviderAttempt attempt, GenerationTask task, long handoffId,
      long handoffGeneration) {
    return new AttemptClaim(attempt.getId(), task.getId(), task.getUserId(),
        task.getApplicationId(), attempt.getChannelId(), attempt.getAttemptKey(),
        attempt.getProviderTaskId(), attempt.getState(), attempt.getOwner(),
        attempt.getGeneration() == null ? 0L : attempt.getGeneration(), handoffId,
        handoffGeneration, value(attempt.getSubmitAttempts()),
        value(attempt.getQueryAttempts()), task.getModelVersion(), task.getImageSize(),
        task.getOutputCount(), task.getPrompt(), task.getNegativePrompt(),
        tasks.selectMediaIds(task.getId()).size());
  }

  private int value(Integer number) {
    return number == null ? 0 : number;
  }
}
