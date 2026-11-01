package com.aiplatform.generation.service;

import com.aiplatform.generation.GenerationAccountingState;
import com.aiplatform.generation.GenerationCancelState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.config.ResultDeliveryProperties;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.provider.ProviderAdapterRouter;
import com.aiplatform.generation.provider.ProviderCancelResult;
import com.aiplatform.generation.provider.ProviderCapabilities;
import com.aiplatform.generation.provider.ProviderTemporaryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 取消工作者：先持久请求再调用渠道；渠道不支持、查不到或超时都不等于取消成功。
 * 只有上游确认取消或确认从未提交时才释放冻结，其余情况保留冻结并继续对账。
 */
@Slf4j
@Service
public class GenerationCancelService {
  private final GenerationCancelTransactions transactions;
  private final GenerationSettlementTransactions settlements;
  private final GenerationUpstreamTransactions upstream;
  private final GenerationTaskMapper tasks;
  private final ProviderAttemptMapper attempts;
  private final ProviderAdapterRouter adapter;
  private final ResultDeliveryProperties properties;

  public GenerationCancelService(GenerationCancelTransactions transactions,
      GenerationSettlementTransactions settlements, GenerationUpstreamTransactions upstream,
      GenerationTaskMapper tasks, ProviderAttemptMapper attempts, ProviderAdapterRouter adapter,
      ResultDeliveryProperties properties) {
    this.transactions = transactions;
    this.settlements = settlements;
    this.upstream = upstream;
    this.tasks = tasks;
    this.attempts = attempts;
    this.adapter = adapter;
    this.properties = properties;
  }

  public void processBatch() {
    if (!properties.isEnabled()) {
      return;
    }
    for (CancelClaim claim : transactions.claimDue(properties.getCancelBatch())) {
      try {
        process(claim);
      } catch (RuntimeException failure) {
        retry(claim, "CANCEL_WORKER_FAILED: " + failure.getClass().getSimpleName());
      }
    }
  }

  private void process(CancelClaim claim) {
    GenerationTask task = tasks.selectById(claim.taskId());
    if (task == null) {
      transactions.finish(claim, GenerationCancelState.REJECTED, "TASK_NOT_FOUND");
      return;
    }
    if (!GenerationAccountingState.FROZEN.equals(task.getAccountingState())) {
      transactions.finish(claim, GenerationCancelState.REJECTED, "ALREADY_TERMINAL");
      return;
    }
    if (terminalTaskState(task.getState())) {
      transactions.finish(claim, GenerationCancelState.REJECTED, "TASK_NOT_CANCELLABLE");
      return;
    }
    ProviderAttempt attempt = attempts.selectByTaskId(claim.taskId());
    if (attempt == null) {
      if (transactions.settleWithoutAttempt(claim)) {
        return;
      }
      GenerationTask fresh = tasks.selectById(claim.taskId());
      if (fresh == null || !GenerationAccountingState.FROZEN.equals(fresh.getAccountingState())) {
        transactions.finish(claim, GenerationCancelState.REJECTED, "ALREADY_TERMINAL");
      } else {
        retry(claim, "ATTEMPT_STATE_PENDING");
      }
      return;
    }
    if (ProviderAttemptState.isTerminal(attempt.getState())) {
      // 上游已有终态事实：先按事实完成结算，再关闭取消请求；绝不当作取消成功。
      try {
        settlements.recover(claim.taskId());
      } catch (RuntimeException failure) {
        retry(claim, "TERMINAL_SETTLEMENT_PENDING");
        return;
      }
      // 上游成功时取消必然不生效：恢复任务状态，让结果交付与唯一结算继续推进，
      // 否则任务会卡在 CANCEL_REQUESTED，既不再保存结果也不能结算。
      transactions.restore(claim, GenerationCancelState.REJECTED, "UPSTREAM_ALREADY_TERMINAL");
      return;
    }
    ProviderCapabilities capabilities = adapter.capabilitiesFor(attempt.getAttemptKey());
    if (!capabilities.cancel()) {
      transactions.restore(claim, GenerationCancelState.UNSUPPORTED, "CHANNEL_UNSUPPORTED");
      return;
    }
    ProviderCancelResult result;
    try {
      result = adapter.cancel(attempt.getAttemptKey(), attempt.getProviderTaskId());
    } catch (ProviderTemporaryException temporary) {
      retry(claim, temporary.code());
      return;
    } catch (RuntimeException failure) {
      retry(claim, "CANCEL_UNKNOWN: " + failure.getClass().getSimpleName());
      return;
    }
    switch (result.status()) {
      case CANCELLED -> {
        boolean applied = upstream.confirmCancel(claim.taskId(), task.getUserId(),
            attempt.getChannelId(), attempt.getId());
        transactions.finish(claim, applied ? GenerationCancelState.RESOLVED
                : GenerationCancelState.REJECTED,
            applied ? "UPSTREAM_CANCELLED" : "UPSTREAM_ALREADY_TERMINAL");
      }
      case UNSUPPORTED ->
          transactions.restore(claim, GenerationCancelState.UNSUPPORTED, "CHANNEL_UNSUPPORTED");
      case TERMINAL -> {
        // 上游已终态但不等于取消成功：恢复执行态，交由查询与结算恢复处理。
        try {
          settlements.recover(claim.taskId());
        } catch (RuntimeException failure) {
          retry(claim, "TERMINAL_SETTLEMENT_PENDING");
          return;
        }
        transactions.restore(claim, GenerationCancelState.REJECTED,
            "UPSTREAM_ALREADY_TERMINAL");
      }
      case NOT_FOUND -> retry(claim, "UPSTREAM_NOT_FOUND");
      default -> retry(claim, "CANCEL_UNKNOWN");
    }
  }

  private boolean terminalTaskState(String state) {
    return GenerationTaskState.isSettledFinal(state)
        || GenerationTaskState.RESULT_PENDING.equals(state)
        || GenerationTaskState.FAILED_PENDING_FOLLOWUP.equals(state)
        || GenerationTaskState.CANCELLED_PENDING_FOLLOWUP.equals(state)
        || GenerationTaskState.QUEUE_TIMEOUT.equals(state);
  }

  private void retry(CancelClaim claim, String error) {
    int exponent = Math.min(Math.max(claim.attempts() - 1, 0), 10);
    long value = (long) properties.getRetryBaseSeconds() << exponent;
    int delay = (int) Math.min(value, properties.getCancelMaxBackoffSeconds());
    transactions.retry(claim, error, delay);
  }
}
