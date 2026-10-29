package com.aiplatform.cache;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 检查点写入与读取；位点恢复仍由 Canal 服务端负责。 */
@Mapper
public interface CacheCheckpointMapper {
  int saveCheckpoint(
      @Param("destination") String destination,
      @Param("batchId") Long batchId,
      @Param("binlogFile") String binlogFile,
      @Param("binlogOffset") Long binlogOffset,
      @Param("eventTimeMs") Long eventTimeMs);

  CacheCheckpoint selectByDestination(@Param("destination") String destination);
}
