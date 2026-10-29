package com.aiplatform.media.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MediaQuotaDTO {
  private final long usedBytes;
  private final int assetCount;
  private final long byteLimit;
  private final int assetLimit;
}
