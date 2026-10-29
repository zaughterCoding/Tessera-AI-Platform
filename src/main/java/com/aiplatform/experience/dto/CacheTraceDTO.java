package com.aiplatform.experience.dto;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class CacheTraceDTO {
  private Long applicationId;
  private String applicationName;
  private List<ReadSample> reads;

  @Data
  public static class ReadSample {
    private int sequence;
    private long durationMicros;
    private List<Map<String, String>> events;
  }
}
