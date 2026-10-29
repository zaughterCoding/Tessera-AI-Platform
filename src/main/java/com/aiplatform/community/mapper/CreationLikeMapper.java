package com.aiplatform.community.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CreationLikeMapper {
  int insertIgnore(@Param("creationId") long creationId, @Param("userId") long userId);

  int delete(@Param("creationId") long creationId, @Param("userId") long userId);

  long exists(@Param("creationId") long creationId, @Param("userId") long userId);

  List<Long> selectLikedIds(@Param("userId") long userId,
      @Param("ids") List<Long> ids);
}
