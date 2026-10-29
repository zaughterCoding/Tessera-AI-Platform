package com.aiplatform.community.mapper;

import com.aiplatform.community.entity.CreationMedia;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CreationMediaMapper {
  int insert(@Param("creationId") long creationId,
      @Param("mediaId") long mediaId,
      @Param("position") int position);

  int deleteByCreation(@Param("creationId") long creationId);

  List<CreationMedia> selectByCreation(@Param("creationId") long creationId);

  List<CreationMedia> selectByCreationIds(@Param("ids") List<Long> ids);
}
