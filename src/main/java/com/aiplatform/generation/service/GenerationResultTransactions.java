package com.aiplatform.generation.service;

import com.aiplatform.generation.GenerationResultState;
import com.aiplatform.generation.GenerationTaskState;
import com.aiplatform.generation.ResultManifest;
import com.aiplatform.generation.config.ResultDeliveryProperties;
import com.aiplatform.generation.entity.GenerationResult;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.mapper.GenerationResultMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.provider.ProviderResultRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 结果登记、认领与保存状态只做短数据库事务；文件与网络操作在事务外完成。 */
@Service
public class GenerationResultTransactions {
  private static final int NOTE_LIMIT = 255;

  private final GenerationResultMapper results;
  private final GenerationTaskMapper tasks;
  private final ResultDeliveryProperties properties;

  public GenerationResultTransactions(GenerationResultMapper results,
      GenerationTaskMapper tasks, ResultDeliveryProperties properties) {
    this.results = results;
    this.tasks = tasks;
    this.properties = properties;
  }

  /** 只有完整清单才登记；清单缺失或数量不符时保持待核对，不生成任何占位结果。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public int seed(long taskId, long attemptId, int outputCount,
      List<ProviderResultRef> manifest) {
    List<ProviderResultRef> ordered = ResultManifest.complete(manifest, outputCount);
    if (ordered == null) {
      return 0;
    }
    int inserted = 0;
    for (ProviderResultRef item : ordered) {
      inserted += results.insertIgnore(taskId, attemptId, item.position(), item.ref(),
          item.digest());
    }
    return inserted;
  }

  @Transactional
  public List<ResultClaim> claimDue() {
    List<GenerationResultMapper.ResultClaimRow> rows = results.selectDue(properties.getBatch());
    List<ResultClaim> claims = new ArrayList<>();
    for (GenerationResultMapper.ResultClaimRow row : rows) {
      String owner = UUID.randomUUID().toString();
      if (results.claim(row.getId(), owner, properties.getLeaseSeconds()) != 1) {
        continue;
      }
      GenerationResult fresh = results.selectById(row.getId());
      if (fresh == null) {
        continue;
      }
      claims.add(new ResultClaim(fresh.getId(), fresh.getTaskId(), fresh.getAttemptId(),
          row.getUserId(), fresh.getPosition(), fresh.getSourceRef(),
          fresh.getExpectedDigest(), owner, fresh.getGeneration(),
          fresh.getSaveAttempts() == null ? 1 : fresh.getSaveAttempts(),
          row.getTaskOutputCount() == null ? 0 : row.getTaskOutputCount()));
    }
    return claims;
  }

  @Transactional
  public boolean markSaved(ResultClaim claim, long mediaId, String contentDigest,
      String contentType, long sizeBytes) {
    return results.markSaved(claim.resultId(), claim.owner(), claim.generation(), mediaId,
        contentDigest, contentType, sizeBytes) == 1;
  }

  /** 失败按结果身份退避；达到上限后任务转人工核对，冻结次数仍保留。 */
  @Transactional
  public void fail(ResultClaim claim, String error) {
    boolean finalFailure = claim.saveAttempts() >= properties.getMaxSaveAttempts();
    int delay = backoffSeconds(claim.saveAttempts());
    if (results.fail(claim.resultId(), claim.owner(), claim.generation(), finalFailure,
        truncate(error), delay) == 1 && finalFailure) {
      tasks.markReviewAfterSaveFailure(claim.taskId(),
          "结果文件保存失败，已转人工核对；冻结次数保留");
    }
  }

  @Transactional
  public boolean resetFailed(long taskId) {
    int changed = results.resetFailed(taskId);
    if (changed == 0) {
      return false;
    }
    GenerationTask task = tasks.selectById(taskId);
    if (task != null && GenerationTaskState.REVIEW.equals(task.getState())) {
      tasks.updateStateIn(taskId, GenerationTaskState.RESULT_PENDING,
          "管理重试结果保存，等待重新拉取", List.of(GenerationTaskState.REVIEW));
    }
    return true;
  }

  public boolean isComplete(long taskId, int outputCount) {
    return outputCount > 0 && results.countByTaskId(taskId) == outputCount;
  }

  public long countSaved(long taskId) {
    return results.countByTaskState(taskId, GenerationResultState.SAVED);
  }

  private int backoffSeconds(int attempts) {
    int exponent = Math.min(Math.max(attempts - 1, 0), 10);
    long value = (long) properties.getRetryBaseSeconds() << exponent;
    return (int) Math.min(value, properties.getRetryMaxSeconds());
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= NOTE_LIMIT ? value : value.substring(0, NOTE_LIMIT);
  }
}
