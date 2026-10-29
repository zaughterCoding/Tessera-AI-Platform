package com.aiplatform.cache;

public interface ICacheCheckpointService {
  void saveCheckpoint(
      String destination, Long batchId, String binlogFile, Long binlogOffset, Long eventTimeMs);

  CacheCheckpoint latest(String destination);
}
