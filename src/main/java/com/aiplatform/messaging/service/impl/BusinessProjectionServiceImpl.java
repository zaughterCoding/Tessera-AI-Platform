package com.aiplatform.messaging.service.impl;

import com.aiplatform.cache.CacheClient;
import com.aiplatform.cache.CacheUnavailableException;
import com.aiplatform.messaging.BusinessEventType;
import com.aiplatform.messaging.entity.BusinessOutboxTask;
import com.aiplatform.messaging.service.IBusinessProjectionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 只处理当前阶段真实注册的事件；缓存内容可重建，数据库任务才是事实。 */
@Service
public class BusinessProjectionServiceImpl implements IBusinessProjectionService {
  private final CacheClient cache;
  private final String prefix;

  public BusinessProjectionServiceImpl(
      CacheClient cache, @Value("${app.redis.key-prefix}") String prefix) {
    this.cache = cache;
    this.prefix = prefix;
  }

  @Override
  public void project(BusinessOutboxTask task) {
    BusinessEventType type = BusinessEventType.from(task.getKind());
    switch (type) {
      case APPLICATION_INVALIDATE:
        invalidateApplication(task.getAggregateId());
        return;
      default:
        throw new IllegalArgumentException("没有可用的投影处理器");
    }
  }

  private void invalidateApplication(long applicationId) {
    String base = prefix + "catalog:application:";
    boolean detail = cache.invalidate(base + applicationId);
    boolean products = cache.invalidate(prefix + "catalog:product:list:" + applicationId);
    int search = cache.invalidatePrefix(prefix + "catalog:application:search:");
    if (!detail || !products || search < 0) {
      throw new CacheUnavailableException("目录缓存失效未全部完成，等待任务重试");
    }
  }
}
