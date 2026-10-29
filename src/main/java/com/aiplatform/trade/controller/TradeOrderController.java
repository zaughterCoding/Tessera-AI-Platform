package com.aiplatform.trade.controller;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.trade.dto.MockPaymentForm;
import com.aiplatform.trade.dto.PurchaseOrderDTO;
import com.aiplatform.trade.dto.PurchaseOrderForm;
import com.aiplatform.trade.dto.TradeOrderQueryForm;
import com.aiplatform.trade.service.ITradeOrderService;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 个人订单与模拟支付入口；服务层显式接收用户 ID。 */
@Validated
@RestController
@RequestMapping("/trade/orders")
public class TradeOrderController {
  private final ITradeOrderService orders;

  public TradeOrderController(ITradeOrderService orders) {
    this.orders = orders;
  }

  @PostMapping
  public Result<PurchaseOrderDTO> create(@Valid @RequestBody PurchaseOrderForm form) {
    return Result.ok(orders.create(currentUserId(), form));
  }

  @GetMapping
  public Result<List<PurchaseOrderDTO>> list(@Valid TradeOrderQueryForm form) {
    return orders.list(currentUserId(), form);
  }

  @GetMapping("/{id}")
  public Result<PurchaseOrderDTO> detail(@PathVariable @Positive Long id) {
    return Result.ok(orders.detail(currentUserId(), id));
  }

  @PostMapping("/{id}/pay")
  public Result<PurchaseOrderDTO> pay(
      @PathVariable @Positive Long id,
      @Valid @RequestBody MockPaymentForm form) {
    return Result.ok(orders.pay(currentUserId(), id, form.getExpectedAmount()));
  }

  @PostMapping("/{id}/cancel")
  public Result<PurchaseOrderDTO> cancel(@PathVariable @Positive Long id) {
    return Result.ok(orders.cancel(currentUserId(), id));
  }

  private long currentUserId() {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
    return user.getId();
  }
}
