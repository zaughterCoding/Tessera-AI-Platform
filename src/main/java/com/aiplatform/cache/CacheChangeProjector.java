package com.aiplatform.cache;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.google.protobuf.InvalidProtocolBufferException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 将 binlog 行变更映射为当前目录缓存键的精确失效。
 *
 * <p>只处理应用与商品元数据表；分类表沿用固定 TTL，不进入 CDC 范围。
 */
@Slf4j
@Component
public class CacheChangeProjector {
  static final Set<String> WATCHED_TABLES = Collections.unmodifiableSet(
      new HashSet<>(Arrays.asList("ai_application", "usage_product")));
  private static final String APPLICATION = "ai_application";
  private static final String PRODUCT = "usage_product";
  private final CacheClient cache;
  private final MeterRegistry metrics;
  private final String prefix;

  @Value("${app.cdc.schema:}")
  private String schema;

  public CacheChangeProjector(
      CacheClient cache,
      MeterRegistry metrics,
      @Value("${app.redis.key-prefix}") String prefix) {
    this.cache = cache;
    this.metrics = metrics;
    this.prefix = prefix;
  }

  public boolean isRelevantEntry(CanalEntry.Entry entry) {
    return entry.getEntryType() == CanalEntry.EntryType.ROWDATA
        && schema != null
        && schema.equals(entry.getHeader().getSchemaName())
        && WATCHED_TABLES.contains(entry.getHeader().getTableName());
  }

  /**
   * 仅投影已配置库中的应用/商品变更；返回是否属于需要完成检查点的批次。
   *
   * <p>解析失败、缺字段或不支持的行事件都会抛出异常，让消费端保留位点重放。
   */
  public boolean projectEntryChanges(CanalEntry.Entry entry)
      throws InvalidProtocolBufferException {
    if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) {
      return false;
    }
    CanalEntry.Header header = entry.getHeader();
    if (schema == null || !schema.equals(header.getSchemaName())) {
      return false;
    }
    String table = header.getTableName() == null ? "" : header.getTableName();
    if (!WATCHED_TABLES.contains(table)) {
      return projectUnknownTable(entry, table);
    }
    CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
    log.debug("CDC 投影表={} 事件={} 行数={}",
        table, change.getEventType(), change.getRowDatasCount());
    if (change.getIsDdl()) {
      invalidateTableMetadata(table);
      metrics.counter("aiplatform.cdc.ddl", "table", table).increment();
      return true;
    }
    if (change.getRowDatasCount() == 0) {
      return true;
    }
    for (CanalEntry.RowData row : change.getRowDatasList()) {
      projectRow(table, change.getEventType(), row);
      metrics.counter("aiplatform.cdc.rows", "table", table).increment();
    }
    return true;
  }

  private boolean projectUnknownTable(CanalEntry.Entry entry, String table)
      throws InvalidProtocolBufferException {
    // 同库未知表的普通行事件不属于目录范围，直接忽略。
    if (!table.isEmpty()) {
      return false;
    }
    CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
    if (change.getIsDdl()) {
      invalidateAllCatalogMetadata();
      metrics.counter("aiplatform.cdc.ddl", "table", "unknown").increment();
      return true;
    }
    if (change.getRowDatasCount() > 0) {
      // 行事件缺少表名无法安全映射，必须保留位点并报警，而不是静默确认。
      throw new IllegalStateException("CDC 行事件缺少表名");
    }
    // 无行镜像的事务性查询事件不属于目录变更。
    return false;
  }

  private void invalidateTableMetadata(String table) {
    if (APPLICATION.equals(table)) {
      invalidatePrefix("catalog:application:");
    } else if (PRODUCT.equals(table)) {
      // 该前缀同时覆盖商品详情与商品列表，列表内容随商品表结构变化。
      invalidatePrefix("catalog:product:");
    }
  }

  private void invalidateAllCatalogMetadata() {
    int applications = cache.invalidatePrefix(prefix + "catalog:application:");
    int products = cache.invalidatePrefix(prefix + "catalog:product:");
    boolean categories = cache.invalidate(prefix + "catalog:category:list");
    if (applications < 0 || products < 0 || !categories) {
      throw new CacheUnavailableException("目录结构变更的缓存失效未全部完成");
    }
  }

  private void invalidatePrefix(String suffix) {
    if (cache.invalidatePrefix(prefix + suffix) < 0) {
      throw new CacheUnavailableException("缓存前缀失效失败: " + suffix);
    }
  }

  private void projectRow(
      String table, CanalEntry.EventType eventType, CanalEntry.RowData row) {
    Map<String, String> before = mapColumnValues(row.getBeforeColumnsList());
    Map<String, String> after = mapColumnValues(row.getAfterColumnsList());
    requireRowImage(eventType, before, after);
    if (APPLICATION.equals(table)) {
      projectApplication(before, after);
    } else if (PRODUCT.equals(table)) {
      projectProduct(before, after);
    }
  }

  private void projectApplication(Map<String, String> before, Map<String, String> after) {
    Set<Long> ids = idsOf(before, after, "id");
    if (ids.isEmpty()) {
      throw new IllegalStateException("应用变更缺少主键 id");
    }
    boolean complete = true;
    for (Long id : ids) {
      if (!cache.invalidate(prefix + "catalog:application:" + id)) {
        complete = false;
      }
      if (!cache.invalidate(prefix + "catalog:product:list:" + id)) {
        complete = false;
      }
    }
    int search = cache.invalidatePrefix(prefix + "catalog:application:search:");
    if (!complete || search < 0) {
      throw new CacheUnavailableException("应用缓存失效未全部完成，保留位点重放");
    }
  }

  private void projectProduct(Map<String, String> before, Map<String, String> after) {
    Set<Long> ids = idsOf(before, after, "id");
    Set<Long> applicationIds = idsOf(before, after, "application_id");
    if (ids.isEmpty()) {
      throw new IllegalStateException("商品变更缺少主键 id");
    }
    if (applicationIds.isEmpty()) {
      throw new IllegalStateException("商品变更缺少关联应用 application_id");
    }
    boolean complete = true;
    for (Long id : ids) {
      if (!cache.invalidate(prefix + "catalog:product:" + id)) {
        complete = false;
      }
    }
    // 商品换应用时旧、新列表都必须失效；DELETE 只保留 before 镜像。
    for (Long applicationId : applicationIds) {
      if (!cache.invalidate(prefix + "catalog:product:list:" + applicationId)) {
        complete = false;
      }
    }
    if (!complete) {
      throw new CacheUnavailableException("商品缓存失效未全部完成，保留位点重放");
    }
  }

  private static void requireRowImage(
      CanalEntry.EventType eventType,
      Map<String, String> before,
      Map<String, String> after) {
    if (eventType == CanalEntry.EventType.INSERT) {
      if (after.isEmpty()) {
        throw new IllegalStateException("INSERT 缺少 after 行镜像");
      }
    } else if (eventType == CanalEntry.EventType.UPDATE) {
      if (before.isEmpty() || after.isEmpty()) {
        throw new IllegalStateException("UPDATE 需要完整 before/after 行镜像");
      }
    } else if (eventType == CanalEntry.EventType.DELETE) {
      if (before.isEmpty()) {
        throw new IllegalStateException("DELETE 缺少 before 行镜像");
      }
    } else {
      throw new IllegalStateException("不支持的 CDC 行事件: " + eventType);
    }
  }

  private static Set<Long> idsOf(
      Map<String, String> before, Map<String, String> after, String column) {
    Set<Long> ids = new LinkedHashSet<>();
    addId(ids, before.get(column), column);
    addId(ids, after.get(column), column);
    return ids;
  }

  private static void addId(Set<Long> ids, String value, String column) {
    if (value == null) {
      return;
    }
    try {
      ids.add(Long.parseLong(value));
    } catch (NumberFormatException invalid) {
      throw new IllegalStateException("binlog 字段不是合法数字: " + column, invalid);
    }
  }

  private static Map<String, String> mapColumnValues(List<CanalEntry.Column> columns) {
    Map<String, String> result = new HashMap<>();
    for (CanalEntry.Column column : columns) {
      if (!column.getIsNull()) {
        result.put(column.getName(), column.getValue());
      }
    }
    return result;
  }
}
