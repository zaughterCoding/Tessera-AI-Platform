package com.aiplatform.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 服务端验证通过的结果文件；source_ref 只用于一次受控拉取，交付后以媒体登记为准。 */
@Data
@TableName("generation_result")
public class GenerationResult {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long taskId;
  private Long attemptId;
  private Integer position;
  private String state;
  private String sourceRef;
  private String expectedDigest;
  private Long mediaId;
  private String contentType;
  private Long sizeBytes;
  private String contentDigest;
  private Integer saveAttempts;
  private String owner;
  private LocalDateTime leaseUntil;
  private Long generation;
  private LocalDateTime nextAttemptAt;
  private String lastError;
  private LocalDateTime savedAt;
  private LocalDateTime deliveredAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
