package com.aiplatform.community.mapper;

import com.aiplatform.community.dto.FollowUserDTO;
import com.aiplatform.community.entity.FollowEdge;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FollowMapper {
  int insertIgnore(@Param("userId") long userId,
      @Param("followUserId") long followUserId);

  int delete(@Param("userId") long userId,
      @Param("followUserId") long followUserId);

  long exists(@Param("userId") long userId,
      @Param("followUserId") long followUserId);

  long countFollowers(@Param("userId") long userId);

  long countFollowing(@Param("userId") long userId);

  /** 按自增 id 升序取下一批粉丝；调用方用最后一条 id 继续。 */
  List<FollowEdge> selectFollowerBatch(@Param("followUserId") long followUserId,
      @Param("afterId") long afterId,
      @Param("limit") int limit);

  List<FollowUserDTO> selectCommonFollows(@Param("userId") long userId,
      @Param("otherUserId") long otherUserId,
      @Param("limit") int limit);
}
