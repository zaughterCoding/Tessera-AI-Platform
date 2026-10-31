package com.aiplatform.generation.result;

/** 已完整读取并校验的结果文件；内容只存在于内存或后续临时文件中。 */
public record FetchedResult(byte[] content, String contentType, String extension, String digest) {
  public int sizeBytes() {
    return content.length;
  }
}
