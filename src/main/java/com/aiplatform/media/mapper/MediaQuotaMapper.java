package com.aiplatform.media.mapper;

import com.aiplatform.media.entity.MediaQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MediaQuotaMapper {
  int ensureQuota(@Param("userId") long userId);

  MediaQuota selectForUpdate(@Param("userId") long userId);

  int reserve(@Param("userId") long userId,
      @Param("bytes") long bytes,
      @Param("byteLimit") long byteLimit,
      @Param("assetLimit") int assetLimit);

  int release(@Param("userId") long userId, @Param("bytes") long bytes);
}
