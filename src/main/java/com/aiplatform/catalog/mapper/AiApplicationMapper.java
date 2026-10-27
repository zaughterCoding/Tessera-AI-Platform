package com.aiplatform.catalog.mapper;

import com.aiplatform.catalog.entity.AiApplication;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiApplicationMapper extends BaseMapper<AiApplication> {
  @Select("SELECT status FROM ai_application WHERE id=#{id}")
  Integer selectStatus(@Param("id") Long id);

  @Select("SELECT COUNT(*) FROM ai_application WHERE name=#{name} AND id<>#{id}")
  int countOtherByName(@Param("name") String name, @Param("id") Long id);
}
