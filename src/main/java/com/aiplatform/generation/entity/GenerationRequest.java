package com.aiplatform.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 请求键幂等台账；任务号只在同一事务内回填，不提供换任务入口。 */
@Data
@TableName("generation_request")
public class GenerationRequest {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private String requestKey;
  private String requestDigest;
  private Long taskId;
  private LocalDateTime createdAt;
}
