package com.aiplatform.catalog.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.aiplatform.analytics.service.IAnalyticsJournalService;
import com.aiplatform.cache.CacheClient;
import com.aiplatform.catalog.dto.ApplicationAdminUpdateForm;
import com.aiplatform.catalog.dto.ApplicationDetailDTO;
import com.aiplatform.catalog.dto.ApplicationPage;
import com.aiplatform.catalog.dto.ApplicationSearchForm;
import com.aiplatform.catalog.dto.ApplicationSummaryDTO;
import com.aiplatform.catalog.dto.CategoryDTO;
import com.aiplatform.catalog.entity.AiApplication;
import com.aiplatform.catalog.mapper.AiApplicationMapper;
import com.aiplatform.catalog.mapper.ApplicationCategoryMapper;
import com.aiplatform.catalog.service.IApplicationService;
import com.aiplatform.catalog.service.ICategoryService;
import com.aiplatform.common.api.ApiException;
import com.aiplatform.common.api.ErrorCode;
import com.aiplatform.common.api.Result;
import com.aiplatform.identity.dto.UserDTO;
import com.aiplatform.identity.security.UserHolder;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.service.IBusinessOutboxService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationServiceImpl
    extends ServiceImpl<AiApplicationMapper, AiApplication>
    implements IApplicationService {
  private static final int STATUS_PUBLISHED = 1;
  private static final long DETAIL_MINUTES = 5;
  private static final long SEARCH_SECONDS = 30;
  private final CacheClient cache;
  private final ICategoryService categories;
  private final ApplicationCategoryMapper categoryMapper;
  private final IBusinessOutboxService outbox;
  private final IAnalyticsJournalService analytics;
  private final String prefix;

  public ApplicationServiceImpl(
      CacheClient cache,
      ICategoryService categories,
      ApplicationCategoryMapper categoryMapper,
      IBusinessOutboxService outbox,
      IAnalyticsJournalService analytics,
      @Value("${app.redis.key-prefix}") String prefix) {
    this.cache = cache;
    this.categories = categories;
    this.categoryMapper = categoryMapper;
    this.outbox = outbox;
    this.analytics = analytics;
    this.prefix = prefix;
  }

  @Override
  public Result<List<ApplicationSummaryDTO>> search(ApplicationSearchForm form) {
    String sort = form.getSort() == null ? "score" : form.getSort();
    String keyword = trimToNull(form.getKeyword());
    String key = prefix + "catalog:application:search:" + searchHash(form, sort, keyword);
    ApplicationPage page = cache.query(key, ApplicationPage.class,
        () -> loadSearch(form, sort, keyword), SEARCH_SECONDS, TimeUnit.SECONDS);
    if (page == null) {
      return Result.ok(Collections.<ApplicationSummaryDTO>emptyList(), 0L);
    }
    return Result.ok(page.getRecords(), page.getTotal());
  }

  @Override
  public Result<ApplicationDetailDTO> detail(Long id) {
    ApplicationDetailDTO detail = cache.query(prefix + "catalog:application:" + id,
        ApplicationDetailDTO.class, () -> loadDetail(id), DETAIL_MINUTES, TimeUnit.MINUTES);
    if (detail == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    // 缓存只保存内容；可见性仍以实时数据库状态为准，已下架应用不能继续暴露。
    Integer liveStatus = baseMapper.selectStatus(id);
    if (!Integer.valueOf(STATUS_PUBLISHED).equals(liveStatus)) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    // 只有成功返回可见详情才计一次访问；身份来自服务端会话，采集失败不影响读取。
    UserDTO viewer = UserHolder.getUser();
    analytics.recordVisit(id, viewer == null ? null : viewer.getId());
    return Result.ok(detail);
  }

  @Override
  @Transactional
  public void updateMetadata(Long id, ApplicationAdminUpdateForm form) {
    AiApplication existing = baseMapper.selectById(id);
    if (existing == null) {
      throw new ApiException(ErrorCode.NOT_FOUND);
    }
    AiApplication patch = new AiApplication();
    patch.setId(id);
    boolean changed = false;
    if (form.getCategoryId() != null) {
      if (categoryMapper.selectById(form.getCategoryId()) == null) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      patch.setCategoryId(form.getCategoryId());
      changed = true;
    }
    if (form.getName() != null) {
      String name = form.getName().trim();
      if (name.isEmpty()) {
        throw new ApiException(ErrorCode.INVALID_REQUEST);
      }
      if (baseMapper.countOtherByName(name, id) > 0) {
        throw new ApiException(ErrorCode.CONFLICT);
      }
      patch.setName(name);
      changed = true;
    }
    changed |= apply(patch::setSummary, form.getSummary());
    changed |= apply(patch::setDescription, form.getDescription());
    changed |= apply(patch::setCapabilities, form.getCapabilities());
    changed |= apply(patch::setCoverUrl, form.getCoverUrl());
    changed |= apply(patch::setModelName, form.getModelName());
    changed |= apply(patch::setModelVersion, form.getModelVersion());
    if (form.getPriceFrom() != null) {
      patch.setPriceFrom(form.getPriceFrom());
      changed = true;
    }
    if (form.getStatus() != null) {
      patch.setStatus(form.getStatus());
      changed = true;
    }
    if (!changed) {
      throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    try {
      baseMapper.updateById(patch);
    } catch (DuplicateKeyException conflict) {
      throw new ApiException(ErrorCode.CONFLICT);
    }
    UserDTO actor = UserHolder.getUser();
    // 管理写与任务同事务提交；回滚时任务和缓存失效都不会发生。
    outbox.appendEvent(BusinessEventType.APPLICATION_INVALIDATE.name(), id,
        actor == null ? null : actor.getId());
  }

  private boolean apply(java.util.function.Consumer<String> setter, String value) {
    if (value == null) {
      return false;
    }
    setter.accept(value.trim());
    return true;
  }

  private ApplicationPage loadSearch(
      ApplicationSearchForm form, String sort, String keyword) {
    LambdaQueryWrapper<AiApplication> query = new LambdaQueryWrapper<>();
    query.eq(AiApplication::getStatus, STATUS_PUBLISHED);
    if (form.getCategoryId() != null) {
      query.eq(AiApplication::getCategoryId, form.getCategoryId());
    }
    if (keyword != null) {
      query.and(inner -> inner.like(AiApplication::getName, keyword)
          .or().like(AiApplication::getSummary, keyword)
          .or().like(AiApplication::getCapabilities, keyword));
    }
    applySort(query, sort);
    Page<AiApplication> page = new Page<>(form.getPage(), form.getSize());
    IPage<AiApplication> result = baseMapper.selectPage(page, query);
    Map<Long, String> names = categoryNames();
    List<ApplicationSummaryDTO> records = new ArrayList<>();
    for (AiApplication application : result.getRecords()) {
      records.add(summary(application, names));
    }
    return new ApplicationPage(records, result.getTotal());
  }

  private ApplicationDetailDTO loadDetail(Long id) {
    AiApplication application = baseMapper.selectById(id);
    if (application == null
        || !Integer.valueOf(STATUS_PUBLISHED).equals(application.getStatus())) {
      return null;
    }
    String categoryName = categoryNames().getOrDefault(application.getCategoryId(), "");
    return new ApplicationDetailDTO(application.getId(), application.getCategoryId(),
        categoryName, application.getName(), application.getSummary(),
        application.getDescription(), application.getCapabilities(), application.getCoverUrl(),
        application.getPriceFrom(), application.getScore(), application.getReviewCount(),
        application.getSoldCount(), application.getApplicationVersion(),
        application.getModelName(), application.getModelVersion(), application.getPublishedAt());
  }

  private ApplicationSummaryDTO summary(AiApplication application, Map<Long, String> names) {
    return new ApplicationSummaryDTO(application.getId(), application.getCategoryId(),
        names.getOrDefault(application.getCategoryId(), ""), application.getName(),
        application.getSummary(), application.getCapabilities(), application.getCoverUrl(),
        application.getPriceFrom(), application.getScore(), application.getReviewCount(),
        application.getSoldCount(), application.getApplicationVersion());
  }

  // MyBatis-Plus 的排序 API 使用泛型可变参数，单列排序会触发可控的 unchecked 警告。
  @SuppressWarnings("unchecked")
  private void applySort(LambdaQueryWrapper<AiApplication> query, String sort) {
    if ("price".equals(sort)) {
      query.orderByAsc(AiApplication::getPriceFrom)
          .orderByDesc(AiApplication::getScore)
          .orderByAsc(AiApplication::getId);
    } else if ("newest".equals(sort)) {
      query.orderByDesc(AiApplication::getPublishedAt).orderByAsc(AiApplication::getId);
    } else {
      query.orderByDesc(AiApplication::getScore)
          .orderByAsc(AiApplication::getPriceFrom)
          .orderByAsc(AiApplication::getId);
    }
  }

  private String searchHash(ApplicationSearchForm form, String sort, String keyword) {
    String canonical = (form.getCategoryId() == null ? "" : form.getCategoryId().toString())
        + "|" + (keyword == null ? "" : keyword.toLowerCase(Locale.ROOT))
        + "|" + sort + "|" + form.getPage() + "|" + form.getSize();
    return DigestUtil.sha256Hex(canonical);
  }

  private Map<Long, String> categoryNames() {
    Map<Long, String> names = new HashMap<>();
    for (CategoryDTO category : categories.cachedList()) {
      names.put(category.getId(), category.getName());
    }
    return names;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
