package com.aiplatform.community.mapper;

import com.aiplatform.community.dto.CreationDTO;
import com.aiplatform.community.entity.Creation;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CreationMapper {
  int insert(Creation creation);

  Creation selectById(@Param("id") long id);

  Creation selectByIdForUpdate(@Param("id") long id);

  CreationDTO selectViewById(@Param("id") long id);

  int updateContent(@Param("id") long id,
      @Param("userId") long userId,
      @Param("title") String title,
      @Param("content") String content);

  int updateVisibility(@Param("id") long id,
      @Param("userId") long userId,
      @Param("state") String state,
      @Param("publishedAt") LocalDateTime publishedAt);

  int incrementLike(@Param("id") long id);

  int decrementLike(@Param("id") long id);

  int deleteOwned(@Param("id") long id, @Param("userId") long userId);

  List<CreationDTO> selectPublic(@Param("limit") int limit, @Param("offset") int offset);

  long countPublic();

  List<CreationDTO> selectHot(@Param("limit") int limit, @Param("offset") int offset);

  List<CreationDTO> selectMine(@Param("userId") long userId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countMine(@Param("userId") long userId);

  List<CreationDTO> selectPublishedByAuthor(@Param("userId") long userId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countPublishedByAuthor(@Param("userId") long userId);

  List<CreationDTO> selectPublishedByApplication(@Param("applicationId") long applicationId,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countPublishedByApplication(@Param("applicationId") long applicationId);

  List<CreationDTO> selectPublishedByIds(@Param("ids") List<Long> ids);
}
