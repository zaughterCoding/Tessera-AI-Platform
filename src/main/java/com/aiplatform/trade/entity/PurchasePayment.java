package com.aiplatform.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 支付事实；免费领取保存模拟标记为假的零金额完成记录。 */
@Data
@TableName("purchase_payment")
public class PurchasePayment {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long orderId;
  private Long userId;
  private String channel;
  private Long amount;
  private Boolean simulated;
  private LocalDateTime paidAt;
  private LocalDateTime createdAt;
}
