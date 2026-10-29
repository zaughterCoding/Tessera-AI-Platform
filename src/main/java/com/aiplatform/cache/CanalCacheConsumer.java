package com.aiplatform.cache;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.InetSocketAddress;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 单实例 Canal 消费者：先完成本批全部失效，再写检查点并确认。
 *
 * <p>Canal 服务端 meta 是实际恢复位点；数据库检查点只用于审计与延迟观测。
 */
@Slf4j
@Component
public class CanalCacheConsumer {
  private static final long MIN_LEASE_MILLIS = 3000;
  private static final long IDLE_SLEEP_MILLIS = 100;
  private final CacheChangeProjector projector;
  private final ICacheCheckpointService checkpoints;
  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;
  private final CdcProperties properties;
  private final String namespace;
  private final AtomicLong connected = new AtomicLong();
  private final AtomicLong leader = new AtomicLong();
  private final AtomicLong lastPoll = new AtomicLong();
  private final AtomicLong lastCheckpointAt = new AtomicLong();
  private final AtomicLong enabled = new AtomicLong();
  private final ExecutorService worker;
  private volatile boolean running;
  private volatile CanalConnector active;
  private volatile CdcLeaderLease lease;

  public CanalCacheConsumer(
      CacheChangeProjector projector,
      ICacheCheckpointService checkpoints,
      StringRedisTemplate redis,
      MeterRegistry metrics,
      CdcProperties properties,
      @Value("${app.namespace}") String namespace) {
    this.projector = projector;
    this.checkpoints = checkpoints;
    this.redis = redis;
    this.metrics = metrics;
    this.properties = properties;
    this.namespace = namespace;
    this.worker = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, "cache-cdc-" + properties.getDestination());
      thread.setDaemon(true);
      return thread;
    });
    this.enabled.set(properties.isEnabled() ? 1 : 0);
    metrics.gauge("aiplatform.cdc.enabled", enabled);
    metrics.gauge("aiplatform.cdc.connected", connected);
    metrics.gauge("aiplatform.cdc.leader", leader);
    metrics.gauge("aiplatform.cdc.poll.age.seconds", lastPoll,
        value -> ageSeconds(value.get()));
    metrics.gauge("aiplatform.cdc.checkpoint.age.seconds", lastCheckpointAt,
        value -> ageSeconds(value.get()));
  }

  @PostConstruct
  public void start() {
    if (!properties.isEnabled()) {
      log.info("缓存 CDC 已关闭；业务写入仍通过 Outbox 失效缓存");
      return;
    }
    validate();
    lease = new CdcLeaderLease(redis, namespace, properties.getDestination());
    running = true;
    worker.submit(this::consume);
  }

  private void validate() {
    if (properties.getSchema() == null
        || !properties.getSchema().matches("[A-Za-z0-9_]+")) {
      throw new IllegalArgumentException("CDC schema 名称只允许字母、数字和下划线");
    }
    if (!properties.getDestination().matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("CDC destination 只允许字母、数字、下划线和连字符");
    }
    if (properties.getPort() < 1 || properties.getPort() > 65535) {
      throw new IllegalArgumentException("CDC 端口不在有效范围");
    }
    if (properties.getBatchSize() < 1 || properties.getBatchSize() > 10_000) {
      throw new IllegalArgumentException("CDC 批次大小不在有效范围");
    }
    if (properties.getPollTimeoutMs() < 0 || properties.getLeaseSeconds() < 3) {
      throw new IllegalArgumentException("CDC 轮询超时或租约配置无效");
    }
  }

  private void consume() {
    while (running && !Thread.currentThread().isInterrupted()) {
      try {
        long leaseMillis = TimeUnit.SECONDS.toMillis(properties.getLeaseSeconds());
        if (!lease.tryAcquire(leaseMillis)) {
          leader.set(0);
          sleep(properties.getReconnectMs());
          continue;
        }
        leader.set(1);
        try {
          runSession(leaseMillis);
        } finally {
          leader.set(0);
          releaseLease();
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      } catch (RuntimeException failure) {
        connected.set(0);
        metrics.counter("aiplatform.cdc.connections", "result", "error").increment();
        if (running) {
          log.warn("Canal 暂不可用，将重连: {}", failure.getMessage());
          sleepQuietly(properties.getReconnectMs());
        }
      }
    }
  }

  private void runSession(long leaseMillis) throws InterruptedException {
    CanalConnector connector = CanalConnectors.newSingleConnector(
        new InetSocketAddress(properties.getHost(), properties.getPort()),
        properties.getDestination(), properties.getUsername(), properties.getPassword());
    active = connector;
    try {
      connector.connect();
      connector.subscribe(filter());
      connector.rollback();
      connected.set(1);
      lastPoll.set(System.currentTimeMillis());
      metrics.counter("aiplatform.cdc.connections", "result", "connected").increment();
      refreshCheckpointAge();
      long renewEvery = Math.max(500, leaseMillis / 3);
      long nextRenew = System.currentTimeMillis() + renewEvery;
      while (running && !Thread.currentThread().isInterrupted()) {
        long now = System.currentTimeMillis();
        if (now >= nextRenew) {
          requireLeadership(leaseMillis);
          nextRenew = now + renewEvery;
        }
        Message batch = connector.getWithoutAck(
            properties.getBatchSize(), properties.getPollTimeoutMs(), TimeUnit.MILLISECONDS);
        lastPoll.set(System.currentTimeMillis());
        if (batch.getId() < 0 || batch.getEntries().isEmpty()) {
          Thread.sleep(IDLE_SLEEP_MILLIS);
          continue;
        }
        processBatch(connector, batch);
        nextRenew = System.currentTimeMillis() + renewEvery;
      }
    } finally {
      connected.set(0);
      metrics.counter("aiplatform.cdc.connections", "result", "disconnected").increment();
      disconnect(connector);
      active = null;
    }
  }

  void processBatch(CanalConnector connector, Message batch) {
    try {
      boolean relevant = false;
      for (CanalEntry.Entry entry : batch.getEntries()) {
        relevant |= projector.projectEntryChanges(entry);
      }
      // 只有仍持有租约的消费者才能提交完成检查点并确认批次。
      requireLeadership(TimeUnit.SECONDS.toMillis(properties.getLeaseSeconds()));
      if (!relevant) {
        connector.ack(batch.getId());
        metrics.counter("aiplatform.cdc.batches", "result", "ignored").increment();
        return;
      }
      CanalEntry.Entry tail = batch.getEntries().get(batch.getEntries().size() - 1);
      CanalEntry.Header header = tail.getHeader();
      checkpoints.saveCheckpoint(properties.getDestination(), batch.getId(),
          header.getLogfileName(), header.getLogfileOffset(), header.getExecuteTime());
      lastCheckpointAt.set(System.currentTimeMillis());
      requireLeadership(TimeUnit.SECONDS.toMillis(properties.getLeaseSeconds()));
      connector.ack(batch.getId());
      metrics.counter("aiplatform.cdc.batches", "result", "success").increment();
      recordEventDelay(header.getExecuteTime());
    } catch (Exception failure) {
      metrics.counter("aiplatform.cdc.batches", "result", "retry").increment();
      rollback(connector, batch.getId());
      throw new IllegalStateException("缓存 CDC 批次失败，保留位点重放", failure);
    }
  }

  private void requireLeadership(long leaseMillis) {
    CdcLeaderLease current = lease;
    if (current == null || !current.renew(leaseMillis)) {
      throw new LeaderLostException();
    }
  }

  private void refreshCheckpointAge() {
    try {
      CacheCheckpoint checkpoint = checkpoints.latest(properties.getDestination());
      if (checkpoint == null || checkpoint.getProcessedAt() == null) {
        lastCheckpointAt.set(0);
        return;
      }
      long millis = checkpoint.getProcessedAt()
          .atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
      lastCheckpointAt.set(millis);
    } catch (RuntimeException failure) {
      // 检查点不可读时延迟标为未知，不用 0 伪装成实时。
      lastCheckpointAt.set(0);
      log.warn("CDC 检查点读取失败: {}", failure.getMessage());
    }
  }

  private String filter() {
    return properties.getSchema() + "\\.(ai_application|usage_product)";
  }

  private void recordEventDelay(long executeTime) {
    if (executeTime <= 0) {
      return;
    }
    metrics.timer("aiplatform.cdc.event.delay")
        .record(Math.max(0, System.currentTimeMillis() - executeTime), TimeUnit.MILLISECONDS);
  }

  private void rollback(CanalConnector connector, long batchId) {
    try {
      connector.rollback(batchId);
    } catch (RuntimeException rollbackFailure) {
      log.warn("CDC 批次回滚失败，将由断线重连恢复: {}", rollbackFailure.getMessage());
    }
  }

  private void releaseLease() {
    CdcLeaderLease current = lease;
    if (current == null) {
      return;
    }
    try {
      current.release();
    } catch (RuntimeException failure) {
      log.warn("CDC 主节点锁释放失败，将由租约回收: {}", failure.getMessage());
    }
  }

  private void disconnect(CanalConnector connector) {
    try {
      connector.disconnect();
    } catch (RuntimeException failure) {
      log.debug("Canal 连接关闭失败: {}", failure.getMessage());
    }
  }

  private void sleep(long millis) throws InterruptedException {
    Thread.sleep(Math.max(1, millis));
  }

  private void sleepQuietly(long millis) {
    try {
      sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private static double ageSeconds(long timestamp) {
    if (timestamp <= 0) {
      return Double.NaN;
    }
    return Math.max(0, System.currentTimeMillis() - timestamp) / 1000.0;
  }

  @PreDestroy
  public void stop() {
    running = false;
    worker.shutdownNow();
    CanalConnector connector = active;
    if (connector != null) {
      disconnect(connector);
    }
    try {
      worker.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
    releaseLease();
  }

  private static final class LeaderLostException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }
}
