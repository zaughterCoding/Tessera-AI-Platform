package com.aiplatform.generation.service;

import com.aiplatform.generation.ProviderAttemptState;
import com.aiplatform.generation.ResultManifest;
import com.aiplatform.generation.config.GenerationProperties;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.entity.ProviderAttempt;
import com.aiplatform.generation.entity.ProviderCallbackEvent;
import com.aiplatform.generation.entity.ProviderChannel;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.mapper.ProviderAttemptMapper;
import com.aiplatform.generation.mapper.ProviderCallbackMapper;
import com.aiplatform.generation.mapper.ProviderChannelMapper;
import com.aiplatform.generation.provider.ProviderCallbackPayload;
import com.aiplatform.generation.provider.ProviderResultRef;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 收件箱与业务事实同事务；终态迁移成功后容量才释放，且只释放一次。 */
@Service
public class GenerationCallbackTransactions {
  private static final Set<String> EVENT_TYPES =
      Set.of("ACCEPTED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED");

  private final ProviderChannelMapper channels;
  private final ProviderAttemptMapper attempts;
  private final ProviderCallbackMapper callbacks;
  private final GenerationTaskMapper tasks;
  private final GenerationFactApplier facts;
  private final GenerationResultTransactions resultTx;
  private final GenerationProperties properties;

  public GenerationCallbackTransactions(ProviderChannelMapper channels,
      ProviderAttemptMapper attempts, ProviderCallbackMapper callbacks, GenerationTaskMapper tasks,
      GenerationFactApplier facts, GenerationResultTransactions resultTx,
      GenerationProperties properties) {
    this.channels = channels;
    this.attempts = attempts;
    this.callbacks = callbacks;
    this.tasks = tasks;
    this.facts = facts;
    this.resultTx = resultTx;
    this.properties = properties;
  }

  @Transactional
  public CallbackOutcome apply(String channelCode, ProviderCallbackPayload payload,
      String digest, int payloadSize) {
    ProviderChannel channel = channels.selectByCode(channelCode);
    ProviderAttempt known = attempts.selectByAttemptKey(payload.attemptKey());
    if (channel == null || known == null || !channel.getId().equals(known.getChannelId())) {
      return CallbackOutcome.INVALID;
    }
    if (payload.providerTaskId() != null && known.getProviderTaskId() != null
        && !payload.providerTaskId().equals(known.getProviderTaskId())) {
      return CallbackOutcome.INVALID;
    }
    int inserted = callbacks.insertInboxIgnore(channel.getId(), payload.eventKey(),
        payload.eventType(), known.getId(), digest, payloadSize, "IGNORED");
    ProviderCallbackEvent inbox = callbacks.selectInboxForUpdate(channel.getId(),
        payload.eventKey());
    if (inbox == null) {
      return CallbackOutcome.INVALID;
    }
    if (!digest.equals(inbox.getPayloadDigest())) {
      callbacks.insertConflictIgnore(channel.getId(), payload.eventKey(),
          inbox.getPayloadDigest(), digest, payloadSize);
      return CallbackOutcome.CONFLICT;
    }
    if (inserted != 1) {
      // 同身份同内容已经受理过，不重复产生业务效果。
      return CallbackOutcome.DUPLICATE;
    }
    ProviderAttempt attempt = attempts.selectByAttemptKeyForUpdate(payload.attemptKey());
    if (attempt == null) {
      return CallbackOutcome.INVALID;
    }
    int changed = applyFact(attempt, payload);
    if (changed == 1) {
      callbacks.updateInboxOutcome(inbox.getId(), "APPLIED");
      return CallbackOutcome.APPLIED;
    }
    callbacks.updateInboxOutcome(inbox.getId(), "IGNORED");
    return CallbackOutcome.IGNORED;
  }

  private int applyFact(ProviderAttempt attempt, ProviderCallbackPayload payload) {
    GenerationTask task = tasks.selectById(attempt.getTaskId());
    if (task == null) {
      return 0;
    }
    int changed;
    switch (payload.eventType()) {
      case "ACCEPTED" -> changed = attempts.applyCallbackAccepted(attempt.getId(),
          payload.providerTaskId(), properties.getPollIntervalSeconds());
      case "RUNNING" -> changed = attempts.applyCallbackRunning(attempt.getId(),
          payload.providerTaskId(), properties.getPollIntervalSeconds());
      case "SUCCEEDED" -> changed = attempts.applyCallbackTerminal(attempt.getId(),
          ProviderAttemptState.RESULT_PENDING, payload.providerTaskId(), payload.resultRef(),
          payload.resultDigest(), null, null);
      case "FAILED" -> changed = attempts.applyCallbackTerminal(attempt.getId(),
          ProviderAttemptState.FAILED, payload.providerTaskId(), null, null,
          payload.failureCode(), payload.failureDetail());
      case "CANCELLED" -> changed = attempts.applyCallbackTerminal(attempt.getId(),
          ProviderAttemptState.CANCELLED, payload.providerTaskId(), null, null,
          payload.failureCode(), payload.failureDetail());
      default -> {
        return 0;
      }
    }
    if (changed != 1) {
      // 历史缺陷快照可能已进入 RESULT_PENDING 但缺少结果元数据；完整成功事件只补事实，
      // 不改终态、不重复释放容量，也不重新写任务状态说明。
      if ("SUCCEEDED".equals(payload.eventType())
          && ProviderAttemptState.RESULT_PENDING.equals(attempt.getState())) {
        int repaired = attempts.completePendingResult(attempt.getId(), payload.providerTaskId(),
            payload.resultRef(), payload.resultDigest());
        if (repaired == 1 || payload.results() != null) {
          seedResults(task, attempt, payload.results(), payload.resultRef(),
              payload.resultDigest());
        }
        return repaired;
      }
      return 0;
    }
    String attemptState = switch (payload.eventType()) {
      case "ACCEPTED" -> ProviderAttemptState.ACCEPTED;
      case "RUNNING" -> ProviderAttemptState.RUNNING;
      case "SUCCEEDED" -> ProviderAttemptState.RESULT_PENDING;
      case "FAILED" -> ProviderAttemptState.FAILED;
      case "CANCELLED" -> ProviderAttemptState.CANCELLED;
      default -> throw new IllegalStateException("未知事件类型");
    };
    if (ProviderAttemptState.RESULT_PENDING.equals(attemptState)) {
      seedResults(task, attempt, payload.results(), payload.resultRef(), payload.resultDigest());
    }
    if (ProviderAttemptState.isTerminal(attemptState)) {
      facts.confirmedTerminal(attempt.getTaskId(), task.getUserId(), attempt.getChannelId(),
          attemptState);
    } else {
      tasks.updateStateUnlessFinal(attempt.getTaskId(),
          ProviderAttemptState.taskState(attemptState),
          GenerationFactApplier.noteFor(attemptState));
    }
    return changed;
  }

  /** 清单完整才登记；缺失时任务保持待补查，绝不释放冻结或生成占位结果。 */
  private void seedResults(GenerationTask task, ProviderAttempt attempt,
      List<ProviderResultRef> results, String ref, String digest) {
    if (task.getOutputCount() == null) {
      return;
    }
    List<ProviderResultRef> manifest = ResultManifest.complete(results, task.getOutputCount());
    if (manifest == null) {
      manifest = ResultManifest.legacy(ref, digest, task.getOutputCount());
    }
    if (manifest != null) {
      resultTx.seed(task.getId(), attempt.getId(), task.getOutputCount(), manifest);
    }
  }

  public static boolean supportedEvent(String eventType) {
    return EVENT_TYPES.contains(eventType);
  }
}
