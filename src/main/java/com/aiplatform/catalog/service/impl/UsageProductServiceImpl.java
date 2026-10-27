package com.aiplatform.catalog.service.impl;

import com.aiplatform.cache.CacheClient;
import com.aiplatform.catalog.dto.ProductAdminUpdateForm;
import com.aiplatform.catalog.dto.ProductAvailability;
import com.aiplatform.catalog.dto.ProductCatalog;
import com.aiplatform.catalog.dto.UsageProductDTO;
import com.aiplatform.catalog.dto.UsageProductMeta;
import com.aiplatform.catalog.entity.UsageProduct;
import com.aiplatform.catalog.enums.ProductSaleMode;
import com.aiplatform.catalog.mapper.AiApplicationMapper;
import com.aiplatform.catalog.mapper.UsageProductMapper;
import com.aiplatform.catalog.service.IUsageProductService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageProductServiceImpl
    extends ServiceImpl<UsageProductMapper, UsageProduct>
    implements IUsageProductService {
  private static final int APP_PUBLISHED = 1;
  private static final int PRODUCT_ACTIVE = 1;
  private static final long METADATA_MINUTES = 10;
  private final CacheClient cache;
  private final AiApplicationMapper applications;
  private final IBusinessOutboxService outbox;
  private final String prefix;

  public UsageProductServiceImpl(
      CacheClient cache,
      AiApplicationMapper applications,
      IBusinessOutboxService outbox,
      @Value("${app.redis.key-prefix}") String prefix) {
    this.cache = cache;
    this.applications = applications;
    this.outbox = outbox;
    this.prefix = prefix;
  }

  @Override
  public Result<List<UsageProductDTO>> listByApplication(Long applicationId) {
    Integer appStatus = applications.selectStatus(applicationId);
    if (!Integer.valueOf(APP_PUBLISHED).equals(appStatus)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    ProductCatalog catalog = cache.query(prefix + "catalog:product:list:" + applicationId,
        ProductCatalog.class, () -> loadIds(applicationId), METADATA_MINUTES, TimeUnit.MINUTES);
    List<Long> ids = catalog == null ? Collections.emptyList() : catalog.getIds();
    if (ids.isEmpty()) {
      return Result.ok(Collections.<UsageProductDTO>emptyList());
    }
    List<UsageProductMeta> metas = new ArrayList<>();
    for (Long id : ids) {
      UsageProductMeta meta = cache.query(prefix + "catalog:product:" + id,
          UsageProductMeta.class, () -> loadMeta(id), METADATA_MINUTES, TimeUnit.MINUTES);
      if (meta != null) {
        metas.add(meta);
      }
    }
    Map<Long, ProductAvailability> live = availability(ids);
    List<UsageProductDTO> values = new ArrayList<>();
    for (UsageProductMeta meta : metas) {
      ProductAvailability state = live.get(meta.getId());
      if (!available(state)) {
        continue;
      }
      values.add(view(meta, true));
    }
    return Result.ok(values);
  }

  @Override
  public Result<UsageProductDTO> detail(Long id) {
    UsageProductMeta meta = cache.query(prefix + "catalog:product:" + id,
        UsageProductMeta.class, () -> loadMeta(id), METADATA_MINUTES, TimeUnit.MINUTES);
    if (meta == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    Map<Long, ProductAvailability> live = availability(Collections.singletonList(id));
    ProductAvailability state = live.get(id);
    if (!available(state)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    return Result.ok(view(meta, true));
  }

  @Override
  @Transactional
  public void updateProduct(Long id, ProductAdminUpdateForm form, Long actorId) {
    if (baseMapper.selectById(id) == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    UsageProduct patch = new UsageProduct();
    patch.setId(id);
    boolean changed = false;
    if (form.getTitle() != null) {
      String title = form.getTitle().trim();
      if (title.isEmpty()) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setTitle(title);
      changed = true;
    }
    changed |= apply(patch::setSubtitle, form.getSubtitle());
    changed |= apply(patch::setRules, form.getRules());
    changed |= apply(patch::setImageSize, form.getImageSize());
    if (form.getPrice() != null) {
      if (form.getPrice() < 0) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setPrice(form.getPrice());
      changed = true;
    }
    if (form.getCredits() != null) {
      if (form.getCredits() < 1) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setCredits(form.getCredits());
      changed = true;
    }
    if (form.getValidDays() != null) {
      if (form.getValidDays() < 1) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setValidDays(form.getValidDays());
      changed = true;
    }
    if (form.getSaleMode() != null) {
      if (form.getSaleMode() < 0 || form.getSaleMode() > 1) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setSaleMode(form.getSaleMode());
      changed = true;
    }
    if (form.getOutputCount() != null) {
      if (form.getOutputCount() < 1) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setOutputCount(form.getOutputCount());
      changed = true;
    }
    if (form.getStatus() != null) {
      if (form.getStatus() < 1 || form.getStatus() > 2) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setStatus(form.getStatus());
      changed = true;
    }
    if (!changed) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    // 商品更新与失效任务同事务；回滚时缓存不会被推进，也不会留下任务。
    baseMapper.updateById(patch);
    outbox.appendEvent(BusinessEventType.PRODUCT_INVALIDATE.name(), id, actorId);
  }

  private boolean apply(java.util.function.Consumer<String> setter, String value) {
    if (value == null) {
      return false;
    }
    setter.accept(value.trim());
    return true;
  }

  private UsageProductDTO view(UsageProductMeta meta, boolean live) {
    UsageProductDTO dto = new UsageProductDTO();
    BeanUtils.copyProperties(meta, dto);
    // 限量售卖需要 M06 的活动窗口与库存事实，当前只允许标准商品下单。
    dto.setOrderable(live && meta.getSaleMode() != null
        && meta.getSaleMode().intValue() == ProductSaleMode.STANDARD.code());
    return dto;
  }

  private boolean available(ProductAvailability state) {
    return state != null
        && Integer.valueOf(PRODUCT_ACTIVE).equals(state.getStatus())
        && Integer.valueOf(APP_PUBLISHED).equals(state.getApplicationStatus());
  }

  private Map<Long, ProductAvailability> availability(List<Long> ids) {
    if (ids.isEmpty()) {
      return Collections.emptyMap();
    }
    List<ProductAvailability> rows = baseMapper.selectAvailability(ids);
    Map<Long, ProductAvailability> result = new HashMap<>();
    for (ProductAvailability row : rows) {
      result.put(row.getId(), row);
    }
    return result;
  }

  private ProductCatalog loadIds(Long applicationId) {
    return new ProductCatalog(baseMapper.selectActiveIds(applicationId));
  }

  private UsageProductMeta loadMeta(Long id) {
    UsageProduct product = baseMapper.selectById(id);
    return product == null ? null : meta(product);
  }

  private UsageProductMeta meta(UsageProduct product) {
    return new UsageProductMeta(product.getId(), product.getApplicationId(),
        product.getTitle(), product.getSubtitle(), product.getRules(), product.getPrice(),
        product.getCredits(), product.getValidDays(), product.getSaleMode(),
        product.getApplicationVersion(), product.getModelVersion(), product.getImageSize(),
        product.getOutputCount());
  }
}
