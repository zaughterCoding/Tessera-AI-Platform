package com.aiplatform.entitlement.service.impl;

import com.aiplatform.entitlement.EntitlementSourceException;
import com.aiplatform.entitlement.TrustedEntitlement;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.entity.PurchasePayment;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.enums.OrderStatus;
import com.aiplatform.trade.mapper.PurchasePaymentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.DateTimeException;
import org.springframework.stereotype.Component;

/** 发放依据只来自已提交订单、支付/领取事实和订单快照，不读取商品当前状态。 */
@Component
public class EntitlementSourceVerifier {
  private static final String FREE = FundingChannel.FREE.name();
  private static final String MOCK = FundingChannel.MOCK.name();
  private final PurchasePaymentMapper payments;

  public EntitlementSourceVerifier(PurchasePaymentMapper payments) {
    this.payments = payments;
  }

  public TrustedEntitlement verify(PurchaseOrder order) {
    if (order == null) {
      throw new EntitlementSourceException("ORDER_MISSING", "来源订单不存在");
    }
    if (!OrderStatus.PAID.matches(order.getStatus())) {
      throw new EntitlementSourceException("ORDER_NOT_PAID", "订单尚未完成购买或领取");
    }
    if (!FREE.equals(order.getFunding()) && !MOCK.equals(order.getFunding())) {
      throw new EntitlementSourceException("UNTRUSTED_FUNDING", "订单资金来源不可用于发放");
    }
    if (order.getPaidAt() == null) {
      throw new EntitlementSourceException("MISSING_PAID_AT", "订单缺少可证明的购买完成时间");
    }
    if (order.getCredits() == null || order.getCredits() <= 0
        || order.getValidDays() == null || order.getValidDays() <= 0
        || order.getOutputCount() == null || order.getOutputCount() <= 0) {
      throw new EntitlementSourceException("INVALID_PACKAGE", "订单权益次数或使用规格不完整");
    }
    if (order.getUserId() == null || order.getProductId() == null
        || order.getApplicationId() == null || order.getApplicationName() == null) {
      throw new EntitlementSourceException("INVALID_SNAPSHOT", "订单权益快照不完整");
    }
    verifyPayment(order);
    try {
      return new TrustedEntitlement(order.getId(), order.getUserId(), order.getProductId(),
          order.getApplicationId(), order.getApplicationName(),
          order.getApplicationVersion() == null ? 0 : order.getApplicationVersion(),
          order.getModelVersion() == null ? "" : order.getModelVersion(),
          order.getImageSize() == null ? "" : order.getImageSize(),
          order.getOutputCount(), order.getCredits(), order.getValidDays(),
          order.getFunding(), order.getPaidAt(),
          order.getPaidAt().plusDays(order.getValidDays()));
    } catch (DateTimeException failure) {
      throw new EntitlementSourceException("INVALID_VALIDITY", "订单有效期无法计算");
    }
  }

  private void verifyPayment(PurchaseOrder order) {
    PurchasePayment payment = payments.selectOne(new LambdaQueryWrapper<PurchasePayment>()
        .eq(PurchasePayment::getOrderId, order.getId()));
    if (payment == null) {
      throw new EntitlementSourceException("PAYMENT_MISSING", "订单缺少支付或免费领取完成事实");
    }
    if (!order.getUserId().equals(payment.getUserId())) {
      throw new EntitlementSourceException("PAYMENT_MISMATCH", "支付事实与订单用户不一致");
    }
    if (!order.getFunding().equals(payment.getChannel())) {
      throw new EntitlementSourceException("PAYMENT_MISMATCH", "支付事实渠道与订单不一致");
    }
    boolean free = FREE.equals(order.getFunding());
    long expectedAmount = free ? 0L : order.getAmount();
    boolean expectedSimulated = !free;
    if (payment.getAmount() == null || payment.getAmount() != expectedAmount
        || !Boolean.valueOf(expectedSimulated).equals(payment.getSimulated())) {
      throw new EntitlementSourceException("PAYMENT_MISMATCH", "支付事实金额或性质与订单不一致");
    }
    if (!order.getPaidAt().equals(payment.getPaidAt())) {
      throw new EntitlementSourceException("PAYMENT_TIME_MISMATCH",
          "支付完成时间与订单完成时间不一致");
    }
  }
}
