package com.aiplatform.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 媒体路径只由服务端生成；读取与删除都先校验严格相对路径。 */
@Component
public class MediaStorage {
  private static final Pattern PATH = Pattern.compile(
      "[0-9]{2}/[0-9]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
          + "\\.(jpg|jpeg|png|webp)");
  private static final String TEMP_DIR = ".tmp";

  private final Path root;

  public MediaStorage(MediaConfig config) {
    this.root = config.root();
    try {
      Files.createDirectories(root);
    } catch (IOException failure) {
      throw new IllegalStateException("无法创建媒体根目录", failure);
    }
  }

  public Path root() {
    return root;
  }

  public String allocatePath(long userId, String extension) {
    String uuid = UUID.randomUUID().toString();
    int shard = (uuid.hashCode() & 0x7fffffff) % 100;
    return String.format("%02d/%02d/%s.%s", Math.floorMod(userId, 100), shard, uuid, extension);
  }

  /** 先写临时文件再原子移动，失败时不会留下半个正式文件。 */
  public void store(String relativePath, InputStream content) throws IOException {
    Path target = resolve(relativePath);
    Files.createDirectories(target.getParent());
    Path tempDir = root.resolve(TEMP_DIR);
    Files.createDirectories(tempDir);
    Path temp = tempDir.resolve(UUID.randomUUID().toString() + ".part");
    try {
      Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public boolean exists(String relativePath) {
    Path file = resolve(relativePath);
    return Files.isRegularFile(file);
  }

  public long size(String relativePath) throws IOException {
    return Files.size(resolve(relativePath));
  }

  public Resource open(String relativePath) throws IOException {
    Path file = resolve(relativePath);
    if (!Files.isRegularFile(file)) {
      throw new IOException("媒体文件不存在");
    }
    return new PathResource(file);
  }

  /** 删除幂等；只接受服务端约定格式，路径穿越在解析前即被拒绝。 */
  public void delete(String relativePath) throws IOException {
    Files.deleteIfExists(resolve(relativePath));
  }

  public Path resolve(String relativePath) {
    if (relativePath == null || !PATH.matcher(relativePath).matches()) {
      throw new IllegalArgumentException("媒体路径不合法");
    }
    Path file = root.resolve(relativePath).normalize();
    if (!file.startsWith(root)) {
      throw new IllegalArgumentException("媒体路径越界");
    }
    return file;
  }

  public boolean isTemporary(Path file) {
    Path tempDir = root.resolve(TEMP_DIR);
    return file.startsWith(tempDir);
  }
}
