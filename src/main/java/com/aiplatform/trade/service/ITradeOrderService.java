package com.aiplatform.trade.service;

import com.aiplatform.common.api.Result;
import com.aiplatform.trade.dto.PurchaseOrderDTO;
import com.aiplatform.trade.dto.PurchaseOrderForm;
import com.aiplatform.trade.dto.TradeOrderQueryForm;
import java.util.List;

/** 所有入口都显式接收已认证用户，不依赖后台线程中的 HTTP 上下文。 */
public interface ITradeOrderService {
  PurchaseOrderDTO create(long userId, PurchaseOrderForm form);

  PurchaseOrderDTO pay(long userId, long orderId, long expectedAmount);

  PurchaseOrderDTO detail(long userId, long orderId);

  Result<List<PurchaseOrderDTO>> list(long userId, TradeOrderQueryForm form);

  int closeExpiredOrders(int batch);
}
