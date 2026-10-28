package com.aiplatform.trade.dto;

import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.enums.OrderStatus;
import java.time.LocalDateTime;
import lombok.Data;

/** 订单视图完全来自下单快照，不读取商品当前价格、标题或状态。 */
@Data
public class PurchaseOrderDTO {
  private Long id;
  private Long campaignId;
  private Integer campaignGeneration;
  private Long productId;
  private Long applicationId;
  private String applicationName;
  private Integer applicationVersion;
  private String productTitle;
  private String productSubtitle;
  private String productRules;
  private String modelVersion;
  private String imageSize;
  private Integer outputCount;
  private Integer credits;
  private Integer validDays;
  private Integer saleMode;
  private String currency;
  private Long unitPrice;
  private Long amount;
  private String status;
  private String funding;
  private boolean simulatedPayment;
  private LocalDateTime expireAt;
  private LocalDateTime paidAt;
  private LocalDateTime closedAt;
  private LocalDateTime createdAt;
  private String entitlementNotice;
  private String settlementNotice;

  public static PurchaseOrderDTO from(PurchaseOrder order) {
    PurchaseOrderDTO view = new PurchaseOrderDTO();
    view.setId(order.getId());
    view.setCampaignId(order.getCampaignId());
    view.setCampaignGeneration(order.getCampaignGeneration());
    view.setProductId(order.getProductId());
    view.setApplicationId(order.getApplicationId());
    view.setApplicationName(order.getApplicationName());
    view.setApplicationVersion(order.getApplicationVersion());
    view.setProductTitle(order.getProductTitle());
    view.setProductSubtitle(order.getProductSubtitle());
    view.setProductRules(order.getProductRules());
    view.setModelVersion(order.getModelVersion());
    view.setImageSize(order.getImageSize());
    view.setOutputCount(order.getOutputCount());
    view.setCredits(order.getCredits());
    view.setValidDays(order.getValidDays());
    view.setSaleMode(order.getSaleMode());
    view.setCurrency(order.getCurrency());
    view.setUnitPrice(order.getUnitPrice());
    view.setAmount(order.getAmount());
    view.setStatus(order.getStatus());
    view.setFunding(order.getFunding());
    view.setSimulatedPayment(FundingChannel.MOCK.name().equals(order.getFunding()));
    view.setExpireAt(order.getExpireAt());
    view.setPaidAt(order.getPaidAt());
    view.setClosedAt(order.getClosedAt());
    view.setCreatedAt(order.getCreatedAt());
    view.setEntitlementNotice(entitlementNotice(order.getStatus()));
    view.setSettlementNotice(settlementNotice(order.getStatus(), order.getFunding()));
    return view;
  }

  private static String entitlementNotice(String status) {
    if (OrderStatus.PAID.matches(status)) {
      return "已购买，权益使用功能尚未开放";
    }
    if (OrderStatus.CANCELLED.matches(status)) {
      return "订单已关闭，权益使用功能尚未开放";
    }
    return "支付完成前不会开通权益，权益使用功能尚未开放";
  }

  private static String settlementNotice(String status, String funding) {
    if (OrderStatus.PAID.matches(status)) {
      return FundingChannel.FREE.name().equals(funding)
          ? "免费领取已记录，未发生真实支付"
          : "模拟支付成功，未发生真实扣款";
    }
    if (OrderStatus.CANCELLED.matches(status)) {
      return "订单已关闭，未发生支付";
    }
    return "订单待支付，尚未发生支付";
  }
}
