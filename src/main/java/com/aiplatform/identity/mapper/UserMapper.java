package com.aiplatform.identity.mapper;

import com.aiplatform.identity.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Set;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {
  @Select("SELECT role_code FROM creator_user_role WHERE user_id=#{userId}")
  Set<String> selectRoles(@Param("userId") Long userId);

  @Insert("INSERT INTO creator_user_role(user_id,role_code) VALUES(#{userId},'USER')")
  int insertUserRole(@Param("userId") Long userId);

  @Update("UPDATE creator_user SET nick_name=#{name} WHERE id=#{userId}")
  int updateNickName(@Param("userId") Long userId, @Param("name") String name);
}
