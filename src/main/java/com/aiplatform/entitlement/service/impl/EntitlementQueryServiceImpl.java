package com.aiplatform.entitlement.service.impl;

import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.entitlement.dto.EntitlementBalanceDTO;
import com.aiplatform.entitlement.dto.EntitlementGrantDTO;
import com.aiplatform.entitlement.dto.EntitlementLedgerDTO;
import com.aiplatform.entitlement.entity.EntitlementGrant;
import com.aiplatform.entitlement.entity.EntitlementLedger;
import com.aiplatform.entitlement.mapper.EntitlementGrantMapper;
import com.aiplatform.entitlement.mapper.EntitlementLedgerMapper;
import com.aiplatform.entitlement.service.IEntitlementQueryService;
import com.aiplatform.trade.entity.PurchaseOrder;
import com.aiplatform.trade.enums.FundingChannel;
import com.aiplatform.trade.enums.OrderStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class EntitlementQueryServiceImpl implements IEntitlementQueryService {
  private static final int MAX_PAGE_SIZE = 50;
  private static final String EXPIRED = "EXPIRED";
  private final EntitlementGrantMapper grants;
  private final EntitlementLedgerMapper ledgers;

  public EntitlementQueryServiceImpl(
      EntitlementGrantMapper grants, EntitlementLedgerMapper ledgers) {
    this.grants = grants;
    this.ledgers = ledgers;
  }

  @Override
  public Result<List<EntitlementGrantDTO>> list(long userId, int page, int size) {
    int pageNo = Math.max(1, page);
    int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    LambdaQueryWrapper<EntitlementGrant> query = new LambdaQueryWrapper<>();
    query.eq(EntitlementGrant::getUserId, userId).orderByDesc(EntitlementGrant::getId);
    IPage<EntitlementGrant> result = grants.selectPage(new Page<>(pageNo, pageSize), query);
    LocalDateTime now = grants.selectDatabaseNow();
    List<EntitlementGrantDTO> views = new ArrayList<>();
    for (EntitlementGrant grant : result.getRecords()) {
      views.add(EntitlementGrantDTO.from(grant, now));
    }
    return Result.ok(views, result.getTotal());
  }

  @Override
  public EntitlementGrantDTO detail(long userId, long grantId) {
    EntitlementGrant grant = requireOwned(userId, grantId);
    return EntitlementGrantDTO.from(grant, grants.selectDatabaseNow());
  }

  @Override
  public Result<List<EntitlementLedgerDTO>> ledger(
      long userId, long grantId, int page, int size) {
    requireOwned(userId, grantId);
    int pageNo = Math.max(1, page);
    int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    long offset = (long) (pageNo - 1) * pageSize;
    int safeOffset = (int) Math.min(offset, Integer.MAX_VALUE);
    List<EntitlementLedgerDTO> views = new ArrayList<>();
    for (EntitlementLedger ledger : ledgers.selectByGrant(grantId, pageSize, safeOffset)) {
      views.add(EntitlementLedgerDTO.from(ledger));
    }
    return Result.ok(views, ledgers.countByGrant(grantId));
  }

  @Override
  public EntitlementBalanceDTO balance(long userId) {
    EntitlementBalanceDTO balance = grants.selectBalance(userId);
    return balance == null ? new EntitlementBalanceDTO() : balance;
  }

  @Override
  public String noticeFor(PurchaseOrder order) {
    if (order == null) {
      return "权益尚未发放";
    }
    if (OrderStatus.CANCELLED.matches(order.getStatus())) {
      return "订单已关闭，权益未发放";
    }
    if (!OrderStatus.PAID.matches(order.getStatus())) {
      return "支付完成前不会发放权益";
    }
    EntitlementGrant grant = grants.selectBySource(order.getId(),
        com.aiplatform.entitlement.EntitlementItems.CREDITS);
    if (grant == null) {
      return FundingChannel.FREE.name().equals(order.getFunding())
          ? "领取完成，权益待发放" : "购买完成，权益待发放";
    }
    LocalDateTime now = grants.selectDatabaseNow();
    return EXPIRED.equals(grant.getStatus()) || !grant.getExpiresAt().isAfter(now)
        ? "权益已过期" : "权益已到账";
  }

  @Override
  public Map<Long, String> noticesFor(List<PurchaseOrder> orders) {
    if (orders == null || orders.isEmpty()) {
      return Collections.emptyMap();
    }
    List<Long> orderIds = new ArrayList<>();
    for (PurchaseOrder order : orders) {
      orderIds.add(order.getId());
    }
    LambdaQueryWrapper<EntitlementGrant> query = new LambdaQueryWrapper<>();
    query.eq(EntitlementGrant::getItemKey, com.aiplatform.entitlement.EntitlementItems.CREDITS)
        .in(EntitlementGrant::getOrderId, orderIds);
    Map<Long, EntitlementGrant> byOrder = new HashMap<>();
    for (EntitlementGrant grant : grants.selectList(query)) {
      byOrder.put(grant.getOrderId(), grant);
    }
    LocalDateTime now = grants.selectDatabaseNow();
    Map<Long, String> notices = new HashMap<>();
    for (PurchaseOrder order : orders) {
      notices.put(order.getId(), notice(order, byOrder.get(order.getId()), now));
    }
    return notices;
  }

  private String notice(PurchaseOrder order, EntitlementGrant grant, LocalDateTime now) {
    if (OrderStatus.CANCELLED.matches(order.getStatus())) {
      return "订单已关闭，权益未发放";
    }
    if (!OrderStatus.PAID.matches(order.getStatus())) {
      return "支付完成前不会发放权益";
    }
    if (grant == null) {
      return FundingChannel.FREE.name().equals(order.getFunding())
          ? "领取完成，权益待发放" : "购买完成，权益待发放";
    }
    return EXPIRED.equals(grant.getStatus()) || !grant.getExpiresAt().isAfter(now)
        ? "权益已过期" : "权益已到账";
  }

  private EntitlementGrant requireOwned(long userId, long grantId) {
    EntitlementGrant grant = grants.selectById(grantId);
    if (grant == null || !Objects.equals(grant.getUserId(), userId)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return grant;
  }
}
