package com.aiplatform.media.dto;

import org.springframework.core.io.Resource;

/** 文件内容只在控制器写出，不参与 JSON 序列化。 */
public class MediaContent {
  private final Resource resource;
  private final String contentType;
  private final long sizeBytes;

  public MediaContent(Resource resource, String contentType, long sizeBytes) {
    this.resource = resource;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
  }

  public Resource getResource() {
    return resource;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }
}
