package com.aiplatform.community.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FeedResult {
  private final List<CreationDTO> records;
  private final Long nextTime;
  private final int nextOffset;
  private final boolean hasMore;
}
