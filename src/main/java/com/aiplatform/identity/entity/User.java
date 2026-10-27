package com.aiplatform.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("creator_user")
public class User {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String phone;
  private String password;
  private String nickName;
  private String icon = "";
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
