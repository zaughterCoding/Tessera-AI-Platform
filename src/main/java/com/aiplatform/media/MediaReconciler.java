package com.aiplatform.media;

import com.aiplatform.media.entity.MediaAsset;
import com.aiplatform.media.mapper.MediaAssetMapper;
import com.aiplatform.media.mapper.MediaQuotaMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 收敛文件系统与登记状态：超时 PENDING 回滚配额并删除文件，
 * 滞留 DELETING 完成删除，未登记且超过宽限期的文件按孤儿清理。
 */
@Slf4j
@Component
public class MediaReconciler {
  private static final int BATCH = 100;
  private static final int ORPHAN_BATCH = 200;

  private final MediaAssetMapper assets;
  private final MediaQuotaMapper quotas;
  private final MediaStorage storage;
  private final TransactionTemplate transactions;
  private final boolean enabled;
  private final Duration pendingGrace;
  private final Duration orphanGrace;

  public MediaReconciler(
      MediaAssetMapper assets,
      MediaQuotaMapper quotas,
      MediaStorage storage,
      TransactionTemplate transactions,
      @Value("${app.media.reconcile-enabled:true}") boolean enabled,
      MediaConfig config) {
    this.assets = assets;
    this.quotas = quotas;
    this.storage = storage;
    this.transactions = transactions;
    this.enabled = enabled;
    this.pendingGrace = config.pendingGrace();
    this.orphanGrace = config.orphanGrace();
  }

  @Scheduled(fixedDelayString = "${app.media.reconcile-interval-ms:60000}")
  public void reconcile() {
    if (!enabled) {
      return;
    }
    long pendingSeconds = pendingGrace.getSeconds();
    try {
      List<MediaAsset> stale = assets.selectStalePending(pendingSeconds, BATCH);
      for (MediaAsset asset : stale) {
        failPending(asset);
      }
    } catch (RuntimeException failure) {
      log.warn("媒体 PENDING 清理失败，type={}", failure.getClass().getSimpleName());
    }
    try {
      List<MediaAsset> deleting = assets.selectStaleDeleting(pendingSeconds, BATCH);
      for (MediaAsset asset : deleting) {
        completeDeleting(asset);
      }
    } catch (RuntimeException failure) {
      log.warn("媒体删除补偿失败，type={}", failure.getClass().getSimpleName());
    }
    try {
      sweepOrphans();
    } catch (RuntimeException | IOException failure) {
      log.warn("媒体孤儿文件扫描失败，type={}", failure.getClass().getSimpleName());
    }
  }

  private void failPending(MediaAsset asset) {
    Boolean released = transactions.execute(tx -> {
      if (assets.markFailed(asset.getId()) != 1) {
        return Boolean.FALSE;
      }
      if (quotas.release(asset.getUserId(), asset.getSizeBytes()) != 1) {
        log.warn("媒体清理未释放配额，assetId={}", asset.getId());
      }
      return Boolean.TRUE;
    });
    if (Boolean.TRUE.equals(released)) {
      deleteQuietly(asset);
    }
  }

  private void completeDeleting(MediaAsset asset) {
    Boolean deleted = transactions.execute(tx -> {
      if (assets.markDeleted(asset.getId()) != 1) {
        return Boolean.FALSE;
      }
      if (quotas.release(asset.getUserId(), asset.getSizeBytes()) != 1) {
        log.warn("媒体删除未释放配额，assetId={}", asset.getId());
      }
      return Boolean.TRUE;
    });
    if (Boolean.TRUE.equals(deleted)) {
      deleteQuietly(asset);
    }
  }

  private void deleteQuietly(MediaAsset asset) {
    try {
      storage.delete(asset.getPath());
    } catch (IOException | IllegalArgumentException failure) {
      // 删除失败留下孤儿文件，下一轮孤儿扫描仍可收敛。
      log.debug("媒体文件删除待重试，assetId={}", asset.getId());
    }
  }

  private void sweepOrphans() throws IOException {
    Set<String> retained = new HashSet<>(assets.selectRetainedPaths());
    Instant cutoff = Instant.now().minus(orphanGrace);
    List<Path> orphans;
    try (Stream<Path> walk = Files.walk(storage.root())) {
      orphans = walk.filter(Files::isRegularFile)
          .filter(file -> !retained.contains(relative(file)))
          .filter(file -> olderThan(file, cutoff))
          .limit(ORPHAN_BATCH)
          .collect(java.util.stream.Collectors.toList());
    }
    for (Path orphan : orphans) {
      try {
        Files.deleteIfExists(orphan);
      } catch (IOException failure) {
        log.debug("媒体孤儿文件删除待重试");
      }
    }
  }

  private String relative(Path file) {
    return storage.root().relativize(file).toString().replace('\\', '/');
  }

  private boolean olderThan(Path file, Instant cutoff) {
    try {
      FileTime modified = Files.getLastModifiedTime(file);
      return modified.toInstant().isBefore(cutoff);
    } catch (IOException failure) {
      return false;
    }
  }
}
