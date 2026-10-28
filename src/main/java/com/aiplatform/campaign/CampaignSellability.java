package com.aiplatform.campaign;

import com.aiplatform.trade.dto.OrderProductSnapshot;
import com.aiplatform.trade.mapper.PurchaseOrderMapper;
import org.springframework.stereotype.Component;

/** 活动商品可售性统一以数据库实时状态判定，公开查询、发布、受理与建单共用。 */
@Component
public class CampaignSellability {
  private static final int PRODUCT_ACTIVE = 1;
  private static final int APPLICATION_ACTIVE = 1;
  private static final int LIMITED_SALE_MODE = 1;
  private final PurchaseOrderMapper orders;

  public CampaignSellability(PurchaseOrderMapper orders) {
    this.orders = orders;
  }

  /** 共享锁读取商品与应用；调用方在事务内使用时结果与建单快照一致。 */
  public OrderProductSnapshot load(long productId) {
    return orders.selectOrderProduct(productId);
  }

  public boolean isSellable(OrderProductSnapshot product) {
    return product != null
        && Integer.valueOf(PRODUCT_ACTIVE).equals(product.getProductStatus())
        && Integer.valueOf(APPLICATION_ACTIVE).equals(product.getApplicationStatus())
        && Integer.valueOf(LIMITED_SALE_MODE).equals(product.getSaleMode());
  }
}
