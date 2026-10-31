package com.aiplatform.generation.service;

import com.aiplatform.generation.config.ResultDeliveryProperties;
import com.aiplatform.generation.entity.GenerationTask;
import com.aiplatform.generation.mapper.GenerationResultMapper;
import com.aiplatform.generation.mapper.GenerationTaskMapper;
import com.aiplatform.generation.result.FetchedResult;
import com.aiplatform.generation.result.HttpResultFetcher;
import com.aiplatform.generation.result.ResultFetchException;
import com.aiplatform.media.entity.MediaAsset;
import com.aiplatform.media.service.IMediaService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 结果交付工作者：认领、网络拉取、文件落盘都发生在数据库事务之外；
 * 只有文件完整落盘并登记后才进入唯一结算入口。任何中断都由租约到期后的扫描恢复。
 */
@Slf4j
@Service
public class GenerationResultService {
  private final GenerationResultTransactions resultTx;
  private final GenerationSettlementTransactions settlements;
  private final GenerationResultMapper results;
  private final GenerationTaskMapper tasks;
  private final HttpResultFetcher fetcher;
  private final IMediaService media;
  private final ResultDeliveryProperties properties;

  public GenerationResultService(GenerationResultTransactions resultTx,
      GenerationSettlementTransactions settlements, GenerationResultMapper results,
      GenerationTaskMapper tasks, HttpResultFetcher fetcher, IMediaService media,
      ResultDeliveryProperties properties) {
    this.resultTx = resultTx;
    this.settlements = settlements;
    this.results = results;
    this.tasks = tasks;
    this.fetcher = fetcher;
    this.media = media;
    this.properties = properties;
  }

  public void processBatch() {
    if (!properties.isEnabled()) {
      return;
    }
    List<ResultClaim> claims = resultTx.claimDue();
    for (ResultClaim claim : claims) {
      try {
        process(claim);
      } catch (RuntimeException failure) {
        // 认领租约仍在，下一轮会接管；绝不把未知异常当作交付成功。
        log.warn("结果交付处理异常，resultId={}，type={}", claim.resultId(),
            failure.getClass().getSimpleName());
      }
    }
  }

  public void settleBatch() {
    if (!properties.isEnabled()) {
      return;
    }
    for (GenerationTask task : tasks.selectSettlementCandidates(
        properties.getSettlementBatch())) {
      try {
        settlements.recover(task.getId());
      } catch (RuntimeException failure) {
        log.warn("结算恢复失败，taskId={}，type={}", task.getId(),
            failure.getClass().getSimpleName());
      }
    }
  }

  public void sweepOrphans() {
    if (!properties.isEnabled()) {
      return;
    }
    List<Long> orphans = results.selectUnreferencedGeneratedMedia(
        properties.getOrphanGraceSeconds(), properties.getOrphanBatch());
    for (Long mediaId : orphans) {
      try {
        media.discardGenerated(mediaId);
      } catch (RuntimeException failure) {
        log.warn("生成结果孤儿清理失败，mediaId={}，type={}", mediaId,
            failure.getClass().getSimpleName());
      }
    }
  }

  private void process(ResultClaim claim) {
    FetchedResult fetched;
    try {
      fetched = fetcher.fetch(claim.sourceRef(), claim.expectedDigest());
    } catch (ResultFetchException failure) {
      resultTx.fail(claim, failure.code() + ": " + failure.getMessage());
      return;
    } catch (RuntimeException failure) {
      resultTx.fail(claim, "FETCH_UNKNOWN: " + failure.getClass().getSimpleName());
      return;
    }
    MediaAsset asset = null;
    try {
      asset = media.storeGenerated(claim.userId(), fetched.content(), fetched.contentType(),
          fetched.extension(), "result-" + claim.position() + "." + fetched.extension());
      if (resultTx.markSaved(claim, asset.getId(), fetched.digest(), fetched.contentType(),
          fetched.sizeBytes())) {
        settle(claim.taskId());
        return;
      }
      // 租约已被接管或媒体已失效：本次文件不登记，立即丢弃，等待新责任重试。
      discard(asset.getId());
    } catch (RuntimeException failure) {
      if (asset != null) {
        discard(asset.getId());
      }
      resultTx.fail(claim, "STORE_FAILED: " + failure.getClass().getSimpleName());
    }
  }

  private void settle(long taskId) {
    try {
      settlements.consume(taskId);
    } catch (RuntimeException failure) {
      // 文件已经保存；结算失败由恢复扫描重放，保留冻结而不是伪造成功。
      log.warn("结果已保存但结算待恢复，taskId={}，type={}", taskId,
          failure.getClass().getSimpleName());
    }
  }

  private void discard(long mediaId) {
    try {
      media.discardGenerated(mediaId);
    } catch (RuntimeException failure) {
      log.warn("生成结果文件丢弃待清理，mediaId={}，type={}", mediaId,
          failure.getClass().getSimpleName());
    }
  }
}
