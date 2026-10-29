package com.aiplatform.media.mapper;

import com.aiplatform.media.entity.MediaAsset;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MediaAssetMapper {
  int insertPending(MediaAsset asset);

  MediaAsset selectById(@Param("id") long id);

  MediaAsset selectByPath(@Param("path") String path);

  MediaAsset selectByIdForUpdate(@Param("id") long id);

  int markActive(@Param("id") long id);

  int markFailed(@Param("id") long id);

  int markDeleting(@Param("id") long id);

  int markDeleted(@Param("id") long id);

  List<String> selectRetainedPaths();

  List<MediaAsset> selectStalePending(@Param("graceSeconds") long graceSeconds,
      @Param("limit") int limit);

  List<MediaAsset> selectStaleDeleting(@Param("graceSeconds") long graceSeconds,
      @Param("limit") int limit);

  List<MediaAsset> selectByOwner(@Param("userId") long userId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countByOwner(@Param("userId") long userId);

  long countAllReferences(@Param("mediaId") long mediaId);

  long countPublicReferences(@Param("mediaId") long mediaId);
}
