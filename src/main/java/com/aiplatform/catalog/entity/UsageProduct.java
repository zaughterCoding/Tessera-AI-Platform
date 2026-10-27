package com.aiplatform.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("usage_product")
public class UsageProduct {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long applicationId;
  private String title;
  private String subtitle;
  private String rules;
  private Long price;
  private Integer credits;
  private Integer validDays;
  private Integer saleMode;
  private Integer applicationVersion;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
