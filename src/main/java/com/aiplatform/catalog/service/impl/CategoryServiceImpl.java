package com.aiplatform.catalog.service.impl;

import com.aiplatform.cache.CacheClient;
import com.aiplatform.catalog.dto.CategoryCatalog;
import com.aiplatform.catalog.dto.CategoryDTO;
import com.aiplatform.catalog.entity.ApplicationCategory;
import com.aiplatform.catalog.mapper.ApplicationCategoryMapper;
import com.aiplatform.catalog.service.ICategoryService;
import com.aiplatform.common.api.Result;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CategoryServiceImpl
    extends ServiceImpl<ApplicationCategoryMapper, ApplicationCategory>
    implements ICategoryService {
  private static final long CACHE_MINUTES = 5;
  private final CacheClient cache;
  private final String prefix;

  public CategoryServiceImpl(
      CacheClient cache, @Value("${app.redis.key-prefix}") String prefix) {
    this.cache = cache;
    this.prefix = prefix;
  }

  @Override
  public Result<List<CategoryDTO>> listCategories() {
    return Result.ok(cachedList());
  }

  @Override
  public List<CategoryDTO> cachedList() {
    CategoryCatalog catalog = cache.query(prefix + "catalog:category:list",
        CategoryCatalog.class, this::loadCatalog, CACHE_MINUTES, TimeUnit.MINUTES);
    return catalog == null ? Collections.emptyList() : catalog.getCategories();
  }

  // 排序 API 的泛型可变参数在单列场景只会产生可忽略的 unchecked 警告。
  @SuppressWarnings("unchecked")
  private CategoryCatalog loadCatalog() {
    List<ApplicationCategory> rows = baseMapper.selectList(
        new LambdaQueryWrapper<ApplicationCategory>()
            .orderByAsc(ApplicationCategory::getSort)
            .orderByAsc(ApplicationCategory::getId));
    List<CategoryDTO> values = new ArrayList<>();
    for (ApplicationCategory row : rows) {
      values.add(new CategoryDTO(row.getId(), row.getName(), row.getIcon(), row.getSort()));
    }
    return new CategoryCatalog(values);
  }
}
