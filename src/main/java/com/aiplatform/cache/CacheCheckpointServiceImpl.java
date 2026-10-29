package com.aiplatform.cache;

import org.springframework.stereotype.Service;

@Service
public class CacheCheckpointServiceImpl implements ICacheCheckpointService {
  private final CacheCheckpointMapper mapper;

  public CacheCheckpointServiceImpl(CacheCheckpointMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void saveCheckpoint(
      String destination, Long batchId, String binlogFile, Long binlogOffset, Long eventTimeMs) {
    // 缺字段属于不可确认的批次，不能以空位点写入检查点。
    if (destination == null || destination.isEmpty()
        || batchId == null || binlogFile == null || binlogFile.isEmpty()
        || binlogOffset == null || eventTimeMs == null) {
      throw new IllegalArgumentException("CDC 检查点字段不完整");
    }
    mapper.saveCheckpoint(destination, batchId, binlogFile, binlogOffset, eventTimeMs);
  }

  @Override
  public CacheCheckpoint latest(String destination) {
    return mapper.selectByDestination(destination);
  }
}
