package com.aiplatform.entitlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 任务最终结算的唯一事实；与账务流水同事务写入，任务主键唯一约束防止双结算。 */
@Data
@TableName("generation_settlement")
public class GenerationSettlement {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long taskId;
  private Long grantId;
  private String kind;
  private String releaseTarget;
  private Integer units;
  private Long ledgerId;
  private String reason;
  private LocalDateTime createdAt;
}
