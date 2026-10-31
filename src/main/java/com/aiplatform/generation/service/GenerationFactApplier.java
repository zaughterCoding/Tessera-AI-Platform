package com.aiplatform.generation.service;

import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.mapper.GenerationQuotaMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import org.springframework.stereotype.Component;

/** 终态副作用的唯一入口：先成功迁移尝试行，再释放一次容量与活动任务名额。 */
@Component
public class GenerationFactApplier {
  private final ProviderChannelMapper channels;
  private final GenerationQuotaMapper quotas;
  private final GenerationTaskMapper tasks;

  public GenerationFactApplier(ProviderChannelMapper channels, GenerationQuotaMapper quotas,
      GenerationTaskMapper tasks) {
    this.channels = channels;
    this.quotas = quotas;
    this.tasks = tasks;
  }

  /** 必须在尝试状态已成功迁移的事务内调用；重复调用会因迁移失败被拒绝。 */
  public void confirmedTerminal(long taskId, long userId, long channelId, String attemptState) {
    if (channels.releaseInFlight(channelId) != 1) {
      throw new IllegalStateException("渠道在途计数与终态尝试不一致");
    }
    quotas.release(userId);
    tasks.updateStateUnlessFinal(taskId, ProviderAttemptState.taskState(attemptState),
        noteFor(attemptState));
  }

  public static String noteFor(String attemptState) {
    return switch (attemptState) {
      case ProviderAttemptState.ACCEPTED -> "上游已受理，等待执行";
      case ProviderAttemptState.RUNNING -> "上游执行中";
      case ProviderAttemptState.RECONCILING -> "上游状态核对中，冻结次数保留";
      case ProviderAttemptState.RESULT_PENDING ->
          "上游成功，结果元数据已记录，等待后续交付与结算";
      case ProviderAttemptState.FAILED -> "上游确认失败，等待后续处理；冻结次数保留";
      case ProviderAttemptState.CANCELLED -> "上游已取消，等待后续处理；冻结次数保留";
      case ProviderAttemptState.REVIEW -> "上游状态无法确认，已转人工核对；冻结次数保留";
      case ProviderAttemptState.SUBMITTING -> "调度已认领，正在提交上游";
      default -> "状态更新";
    };
  }

  public static String terminalNote(String taskState) {
    if (GenerationTaskState.QUEUE_TIMEOUT.equals(taskState)) {
      return "排队超过时限，等待后续处理；冻结次数保留";
    }
    return noteFor(taskState);
  }
}
