package com.aiplatform.media.dto;

import com.aiplatform.media.entity.MediaAsset;
import java.time.LocalDateTime;
import lombok.Data;

/** 媒体视图不暴露物理根目录，只给出经过授权的读取地址。 */
@Data
public class MediaAssetDTO {
  private Long id;
  private String state;
  private String contentType;
  private Long sizeBytes;
  private String originalName;
  private String url;
  private LocalDateTime createdAt;

  public static MediaAssetDTO owned(MediaAsset asset) {
    MediaAssetDTO view = new MediaAssetDTO();
    view.setId(asset.getId());
    view.setState(asset.getState());
    view.setContentType(asset.getContentType());
    view.setSizeBytes(asset.getSizeBytes());
    view.setOriginalName(asset.getOriginalName());
    view.setUrl("/media/mine/" + asset.getId());
    view.setCreatedAt(asset.getCreatedAt());
    return view;
  }
}
