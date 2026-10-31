package com.aiplatform.media.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.media.MediaConfig;
import com.aiplatform.media.MediaStorage;
import com.aiplatform.media.dto.MediaAssetDTO;
import com.aiplatform.media.dto.MediaContent;
import com.aiplatform.media.dto.MediaQuotaDTO;
import com.aiplatform.media.entity.MediaAsset;
import com.aiplatform.media.entity.MediaQuota;
import com.aiplatform.media.enums.MediaState;
import com.aiplatform.media.mapper.MediaAssetMapper;
import com.aiplatform.media.mapper.MediaQuotaMapper;
import com.aiplatform.media.service.IMediaService;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class MediaServiceImpl implements IMediaService {
  private static final Map<String, String> TYPES = Collections.unmodifiableMap(
      new java.util.HashMap<String, String>() {
        {
          put("jpg", "image/jpeg");
          put("jpeg", "image/jpeg");
          put("png", "image/png");
          put("webp", "image/webp");
        }
      });

  private final MediaAssetMapper assets;
  private final MediaQuotaMapper quotas;
  private final MediaStorage storage;
  private final MediaConfig config;
  private final IBusinessOutboxService outbox;
  private final TransactionTemplate transactions;

  public MediaServiceImpl(
      MediaAssetMapper assets,
      MediaQuotaMapper quotas,
      MediaStorage storage,
      MediaConfig config,
      IBusinessOutboxService outbox,
      TransactionTemplate transactions) {
    this.assets = assets;
    this.quotas = quotas;
    this.storage = storage;
    this.config = config;
    this.outbox = outbox;
    this.transactions = transactions;
  }

  @Override
  public MediaAssetDTO upload(long userId, MultipartFile file) {
    ValidatedImage image = validate(file);
    String path = storage.allocatePath(userId, image.extension());
    MediaAsset asset = new MediaAsset();
    asset.setUserId(userId);
    asset.setPath(path);
    asset.setOriginalName(image.originalName());
    asset.setContentType(image.contentType());
    asset.setSizeBytes(image.size());
    asset.setState(MediaState.PENDING.name());
    asset.setSource("UPLOAD");
    // 配额预占与登记同事务；登记失败不会留下未释放的配额。
    transactions.executeWithoutResult(tx -> {
      quotas.ensureQuota(userId);
      if (quotas.reserve(userId, image.size(), config.userByteQuota(),
          config.userAssetQuota()) != 1) {
        throw new ApiException(ErrorCode.QUOTA_EXCEEDED);
      }
      assets.insertPending(asset);
    });
    try (InputStream content = file.getInputStream()) {
      storage.store(path, content);
    } catch (IOException | RuntimeException failure) {
      abortUpload(asset.getId(), userId, image.size(), path);
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    try {
      transactions.executeWithoutResult(tx -> {
        if (assets.markActive(asset.getId()) != 1) {
          throw new IllegalStateException("媒体登记状态已变化");
        }
      });
    } catch (RuntimeException failure) {
      // 无法确认 ACTIVE 时响应失败；残留 PENDING 由清理任务回滚配额与文件。
      log.warn("媒体登记完成失败，等待清理，assetId={}", asset.getId());
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    return MediaAssetDTO.owned(requireAsset(asset.getId()));
  }

  @Override
  public MediaAsset storeGenerated(long userId, byte[] content, String contentType,
      String extension, String originalName) {
    if (content == null || content.length == 0 || content.length > config.maxFileBytes()) {
      throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    String expectedType = TYPES.get(extension);
    if (expectedType == null || !expectedType.equals(contentType)) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    byte[] signature = new byte[12];
    System.arraycopy(content, 0, signature, 0, Math.min(content.length, signature.length));
    if (content.length < signature.length || !matches(extension, signature)) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    String path = storage.allocatePath(userId, extension);
    MediaAsset asset = new MediaAsset();
    asset.setUserId(userId);
    asset.setPath(path);
    asset.setOriginalName(safeName(originalName == null ? "result." + extension : originalName,
        extension));
    asset.setContentType(contentType);
    asset.setSizeBytes((long) content.length);
    asset.setState(MediaState.PENDING.name());
    asset.setSource("GENERATED");
    transactions.executeWithoutResult(tx -> {
      quotas.ensureQuota(userId);
      if (quotas.reserve(userId, content.length, config.userByteQuota(),
          config.userAssetQuota()) != 1) {
        throw new ApiException(ErrorCode.QUOTA_EXCEEDED);
      }
      assets.insertPending(asset);
    });
    try (InputStream stream = new ByteArrayInputStream(content)) {
      storage.store(path, stream);
    } catch (IOException | RuntimeException failure) {
      abortUpload(asset.getId(), userId, content.length, path);
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    try {
      transactions.executeWithoutResult(tx -> {
        if (assets.markActive(asset.getId()) != 1) {
          throw new IllegalStateException("生成文件登记状态已变化");
        }
      });
    } catch (RuntimeException failure) {
      log.warn("生成文件登记完成失败，等待清理，assetId={}", asset.getId());
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    return requireAsset(asset.getId());
  }

  @Override
  @Transactional
  public void discardGenerated(long assetId) {
    MediaAsset asset = assets.selectByIdForUpdate(assetId);
    if (asset == null || !"GENERATED".equals(asset.getSource())
        || !MediaState.ACTIVE.name().equals(asset.getState())) {
      return;
    }
    if (assets.countAllReferences(assetId) > 0) {
      return;
    }
    if (assets.markDeleting(assetId) == 1) {
      outbox.appendEvent(BusinessEventType.MEDIA_DELETE.name(), assetId, asset.getUserId());
    }
  }

  @Override
  public MediaQuotaDTO quota(long userId) {
    MediaQuota quota = transactions.execute(tx -> {
      quotas.ensureQuota(userId);
      return quotas.selectForUpdate(userId);
    });
    if (quota == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    return new MediaQuotaDTO(quota.getUsedBytes(), quota.getAssetCount(),
        config.userByteQuota(), config.userAssetQuota());
  }

  @Override
  public List<MediaAssetDTO> listOwned(long userId, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    int safeOffset = Math.max(0, offset);
    return assets.selectByOwner(userId, safeLimit, safeOffset).stream()
        .map(MediaAssetDTO::owned)
        .collect(Collectors.toList());
  }

  @Override
  public MediaContent openOwned(long userId, long assetId) {
    MediaAsset asset = assets.selectById(assetId);
    if (asset == null || asset.getUserId() != userId
        || !MediaState.ACTIVE.name().equals(asset.getState())) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return content(asset);
  }

  @Override
  public MediaContent openPublic(long assetId) {
    MediaAsset asset = assets.selectById(assetId);
    if (asset == null || !MediaState.ACTIVE.name().equals(asset.getState())
        || assets.countPublicReferences(assetId) == 0) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return content(asset);
  }

  @Override
  @Transactional
  public void completeDeletion(long userId, long assetId) {
    MediaAsset asset = assets.selectByIdForUpdate(assetId);
    if (asset == null || !Long.valueOf(userId).equals(asset.getUserId())) {
      throw new IllegalArgumentException("媒体与删除事实身份不一致");
    }
    if (MediaState.DELETED.name().equals(asset.getState())) {
      return;
    }
    if (!MediaState.DELETING.name().equals(asset.getState())) {
      throw new IllegalStateException("媒体状态不允许完成删除");
    }
    try {
      // 先删文件再结算；若事务回滚，重试时删除仍然幂等。
      storage.delete(asset.getPath());
    } catch (IOException failure) {
      throw new IllegalStateException("媒体文件删除失败，等待重试", failure);
    }
    if (assets.markDeleted(assetId) == 1
        && quotas.release(userId, asset.getSizeBytes()) != 1) {
      log.warn("媒体删除未释放配额，userId={}，assetId={}", userId, assetId);
    }
  }

  @Override
  @Transactional
  public MediaAssetDTO delete(long userId, long assetId) {
    MediaAsset asset = assets.selectByIdForUpdate(assetId);
    if (asset == null || asset.getUserId() != userId) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    if (MediaState.DELETED.name().equals(asset.getState())
        || MediaState.FAILED.name().equals(asset.getState())
        || MediaState.DELETING.name().equals(asset.getState())) {
      return MediaAssetDTO.owned(asset);
    }
    if (!MediaState.ACTIVE.name().equals(asset.getState())) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (assets.countAllReferences(assetId) > 0) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    if (assets.markDeleting(assetId) != 1) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    // 文件删除交给同一 Outbox；重复投递与重复清理都必须幂等。
    outbox.appendEvent(BusinessEventType.MEDIA_DELETE.name(), assetId, userId);
    return MediaAssetDTO.owned(requireAsset(assetId));
  }

  @Override
  public List<MediaAsset> lockOwnedAssets(long userId, List<Long> assetIds) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("媒体引用必须在业务事务内锁定");
    }
    if (assetIds == null || assetIds.isEmpty()) {
      return Collections.emptyList();
    }
    TreeSet<Long> ordered = new TreeSet<>();
    for (Long id : assetIds) {
      if (id == null || id <= 0 || !ordered.add(id)) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
    }
    List<MediaAsset> locked = new ArrayList<>();
    // 固定升序加锁，避免不同发布请求以相反顺序交叉等待。
    for (Long id : ordered) {
      MediaAsset asset = assets.selectByIdForUpdate(id);
      if (asset == null || asset.getUserId() != userId
          || !MediaState.ACTIVE.name().equals(asset.getState())) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      locked.add(asset);
    }
    return locked;
  }

  private MediaContent content(MediaAsset asset) {
    try {
      Resource resource = storage.open(asset.getPath());
      return new MediaContent(resource, asset.getContentType(), asset.getSizeBytes());
    } catch (IOException missing) {
      // 登记存在但文件不可读时，不猜测内容或回退到目录浏览。
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
  }

  private MediaAsset requireAsset(long assetId) {
    MediaAsset asset = assets.selectById(assetId);
    if (asset == null) {
      throw new ApiException(ErrorCode.UNAVAILABLE);
    }
    return asset;
  }

  private void abortUpload(long assetId, long userId, long sizeBytes, String path) {
    try {
      transactions.executeWithoutResult(tx -> {
        if (assets.markFailed(assetId) == 1 && quotas.release(userId, sizeBytes) != 1) {
          log.warn("媒体失败回滚未释放配额，userId={}，assetId={}", userId, assetId);
        }
      });
    } catch (RuntimeException failure) {
      log.warn("媒体失败回滚待清理任务重试，assetId={}，type={}",
          assetId, failure.getClass().getSimpleName());
    }
    try {
      storage.delete(path);
    } catch (IOException | IllegalArgumentException ignored) {
      // 文件未写入或已被清理时继续返回失败结果；孤儿扫描会再次处理。
      log.debug("媒体失败回滚未删除文件，assetId={}", assetId);
    }
  }

  private ValidatedImage validate(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() <= 0) {
      throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    if (file.getSize() > config.maxFileBytes()) {
      throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
    }
    String rawName = file.getOriginalFilename();
    if (rawName == null) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    int dot = rawName.lastIndexOf('.');
    if (dot < 0 || dot == rawName.length() - 1) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    String extension = rawName.substring(dot + 1).toLowerCase(Locale.ROOT);
    String contentType = TYPES.get(extension);
    if (contentType == null) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    byte[] signature = new byte[12];
    int read = 0;
    try (InputStream stream = file.getInputStream()) {
      while (read < signature.length) {
        int count = stream.read(signature, read, signature.length - read);
        if (count < 0) {
          break;
        }
        read += count;
      }
    } catch (IOException failure) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    if (read < signature.length || !matches(extension, signature)) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    return new ValidatedImage(extension, contentType, safeName(rawName, extension),
        file.getSize());
  }

  private boolean matches(String extension, byte[] signature) {
    if ("png".equals(extension)) {
      return (signature[0] & 0xff) == 137
          && signature[1] == 80 && signature[2] == 78 && signature[3] == 71;
    }
    if ("jpg".equals(extension) || "jpeg".equals(extension)) {
      return (signature[0] & 0xff) == 255 && (signature[1] & 0xff) == 216
          && (signature[2] & 0xff) == 255;
    }
    return new String(signature, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
        && new String(signature, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
  }

  private String safeName(String original, String extension) {
    String name = original.replace('\\', '/');
    int slash = name.lastIndexOf('/');
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    name = name.replaceAll("\\p{Cntrl}", "").trim();
    if (name.isEmpty()) {
      name = "upload." + extension;
    }
    return name.length() > 128 ? name.substring(0, 128) : name;
  }

  private static final class ValidatedImage {
    private final String extension;
    private final String contentType;
    private final String originalName;
    private final long size;

    private ValidatedImage(String extension, String contentType, String originalName, long size) {
      this.extension = extension;
      this.contentType = contentType;
      this.originalName = originalName;
      this.size = size;
    }

    private String extension() {
      return extension;
    }

    private String contentType() {
      return contentType;
    }

    private String originalName() {
      return originalName;
    }

    private long size() {
      return size;
    }
  }
}
