package com.aiplatform.media;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** 媒体限制在启动时校验；文件根目录必须与数据库、缓存及其他环境隔离。 */
@Configuration
public class MediaConfig {
  private final Path root;
  private final long maxFileBytes;
  private final long userByteQuota;
  private final int userAssetQuota;
  private final int maxCreationMedia;
  private final Duration pendingGrace;
  private final Duration orphanGrace;

  public MediaConfig(
      @Value("${app.media.root:./.local/media}") String root,
      @Value("${app.media.max-file-bytes:5000000}") long maxFileBytes,
      @Value("${app.media.user-byte-quota:500000000}") long userByteQuota,
      @Value("${app.media.user-asset-quota:1000}") int userAssetQuota,
      @Value("${app.media.max-creation-media:9}") int maxCreationMedia,
      @Value("${app.media.pending-grace-seconds:300}") long pendingGraceSeconds,
      @Value("${app.media.orphan-grace-seconds:3600}") long orphanGraceSeconds) {
    if (root == null || root.trim().isEmpty()) {
      throw new IllegalStateException("媒体根目录必须配置");
    }
    if (maxFileBytes < 1 || maxFileBytes > 20_000_000L) {
      throw new IllegalStateException("单文件大小上限必须在 1～20000000 字节之间");
    }
    if (userByteQuota < maxFileBytes) {
      throw new IllegalStateException("用户累计配额不能小于单文件上限");
    }
    if (userAssetQuota < 1 || userAssetQuota > 10_000) {
      throw new IllegalStateException("用户文件数量配额必须在 1～10000 之间");
    }
    if (maxCreationMedia < 0 || maxCreationMedia > 9) {
      throw new IllegalStateException("单个发布最多选择的媒体数量必须在 0～9 之间");
    }
    if (pendingGraceSeconds < 1 || orphanGraceSeconds < 1) {
      throw new IllegalStateException("媒体清理宽限期必须为正数");
    }
    this.root = Paths.get(root).toAbsolutePath().normalize();
    this.maxFileBytes = maxFileBytes;
    this.userByteQuota = userByteQuota;
    this.userAssetQuota = userAssetQuota;
    this.maxCreationMedia = maxCreationMedia;
    this.pendingGrace = Duration.ofSeconds(pendingGraceSeconds);
    this.orphanGrace = Duration.ofSeconds(orphanGraceSeconds);
  }

  public Path root() {
    return root;
  }

  public long maxFileBytes() {
    return maxFileBytes;
  }

  public long userByteQuota() {
    return userByteQuota;
  }

  public int userAssetQuota() {
    return userAssetQuota;
  }

  public int maxCreationMedia() {
    return maxCreationMedia;
  }

  public Duration pendingGrace() {
    return pendingGrace;
  }

  public Duration orphanGrace() {
    return orphanGrace;
  }
}
