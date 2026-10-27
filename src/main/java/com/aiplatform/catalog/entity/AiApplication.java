package com.aiplatform.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("ai_application")
public class AiApplication {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long categoryId;
  private Long ownerUserId;
  private String name;
  private String summary;
  private String description;
  private String capabilities;
  private String coverUrl;
  private String modelName;
  private String modelVersion;
  private Integer applicationVersion;
  private Long priceFrom;
  private Integer score;
  private Integer reviewCount;
  private Integer soldCount;
  private Integer status;
  private LocalDateTime publishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
