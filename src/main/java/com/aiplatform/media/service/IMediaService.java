package com.aiplatform.media.service;

import com.aiplatform.media.dto.MediaAssetDTO;
import com.aiplatform.media.dto.MediaContent;
import com.aiplatform.media.dto.MediaQuotaDTO;
import com.aiplatform.media.entity.MediaAsset;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface IMediaService {
  MediaAssetDTO upload(long userId, MultipartFile file);

  MediaQuotaDTO quota(long userId);

  List<MediaAssetDTO> listOwned(long userId, int limit, int offset);

  MediaContent openOwned(long userId, long assetId);

  MediaContent openPublic(long assetId);

  MediaAssetDTO delete(long userId, long assetId);

  /** 消费 MEDIA_DELETE 事实：删除文件后幂等结算登记与配额。 */
  void completeDeletion(long userId, long assetId);

  /** 结果交付写入生成文件；先登记 PENDING，写入后转 ACTIVE，失败回滚配额。 */
  MediaAsset storeGenerated(long userId, byte[] content, String contentType, String extension,
      String originalName);

  /** 丢弃未被任何结果引用的生成文件；文件删除复用媒体删除事件，重复调用幂等。 */
  void discardGenerated(long assetId);

  /**
   * 在发布事务内按素材 ID 升序锁定并校验归属与可用状态。
   * 调用方必须已经处于数据库事务中；任一素材不可用即拒绝整次发布。
   */
  List<MediaAsset> lockOwnedAssets(long userId, List<Long> assetIds);
}
