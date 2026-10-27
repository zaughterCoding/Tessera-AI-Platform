package com.aiplatform.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("application_category")
public class ApplicationCategory {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String name;
  private String icon;
  private Integer sort;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
