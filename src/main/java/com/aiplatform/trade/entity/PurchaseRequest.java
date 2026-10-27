package com.aiplatform.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 请求键幂等台账；订单号只在同一事务内回填。 */
@Data
@TableName("purchase_request")
public class PurchaseRequest {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long userId;
  private String requestKey;
  private String requestDigest;
  private Long orderId;
  private LocalDateTime createdAt;
}
