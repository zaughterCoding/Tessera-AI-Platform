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

  /**
   * 在发布事务内按素材 ID 升序锁定并校验归属与可用状态。
   * 调用方必须已经处于数据库事务中；任一素材不可用即拒绝整次发布。
   */
  List<MediaAsset> lockOwnedAssets(long userId, List<Long> assetIds);
}
