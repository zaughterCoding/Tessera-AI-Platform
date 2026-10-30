package com.aiplatform.analytics.transport;

import com.aiplatform.analytics.AnalyticsConflictException;
import com.aiplatform.analytics.AnalyticsEvent;
import com.aiplatform.analytics.AnalyticsEventCodec;
import com.aiplatform.analytics.AnalyticsMalformedException;
import com.aiplatform.analytics.AnalyticsProperties;
import com.aiplatform.analytics.mapper.AnalyticsFailureMapper;
import com.aiplatform.analytics.service.IAnalyticsAggregationService;
import com.aiplatform.messaging.FailureMessages;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Kafka 行为通道：浏览有界尽力发送，订单事实等待确认，消费先落库再提交位点。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.analytics.enabled", havingValue = "true")
public class KafkaBehaviorTransport implements AnalyticsTransport {
  private static final String EVENT_METRIC = "aiplatform.analytics.events";

  private final AnalyticsProperties properties;
  private final AnalyticsEventCodec codec;
  private final IAnalyticsAggregationService aggregation;
  private final AnalyticsFailureMapper failures;
  private final MeterRegistry metrics;
  private final AtomicInteger consuming = new AtomicInteger();
  private final AtomicLong lag = new AtomicLong(-1);
  private final ThreadPoolExecutor visits;
  private final Map<TopicPartition, OffsetAndMetadata> committed = new HashMap<>();
  private volatile boolean running;
  private boolean started;
  private volatile KafkaProducer<String, String> producer;
  private volatile KafkaConsumer<String, String> consumer;
  private Thread consumerThread;

  public KafkaBehaviorTransport(
      AnalyticsProperties properties,
      AnalyticsEventCodec codec,
      IAnalyticsAggregationService aggregation,
      AnalyticsFailureMapper failures,
      MeterRegistry metrics) {
    this.properties = properties;
    this.codec = codec;
    this.aggregation = aggregation;
    this.failures = failures;
    this.metrics = metrics;
    this.visits = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(properties.getVisitQueueCapacity()),
        runnable -> {
          Thread thread = new Thread(runnable, "analytics-visit-sender");
          thread.setDaemon(true);
          return thread;
        },
        new ThreadPoolExecutor.AbortPolicy());
    metrics.gauge("aiplatform.analytics.consumer.running", consuming);
    metrics.gauge("aiplatform.analytics.consumer.lag", lag);
    metrics.gauge("aiplatform.analytics.visit.queue", visits,
        pool -> pool.getQueue().size());
  }

  @EventListener(ContextRefreshedEvent.class)
  public synchronized void start() {
    if (started) {
      return;
    }
    started = true;
    running = true;
    try {
      producer = new KafkaProducer<>(producerProperties());
    } catch (RuntimeException failure) {
      // 分析链路不可用时仍要提供目录与交易，浏览事件按丢弃计数。
      log.error("分析生产者创建失败，浏览事件将按丢弃计数，type={}",
          failure.getClass().getSimpleName());
    }
    if (properties.isConsumerEnabled()) {
      consumerThread = new Thread(this::consumeLoop, "analytics-kafka-consumer");
      consumerThread.setDaemon(true);
      consumerThread.start();
    }
  }

  @Override
  public void submitVisit(AnalyticsEvent event) {
    try {
      visits.execute(() -> sendVisit(event));
    } catch (RejectedExecutionException full) {
      metrics.counter("aiplatform.analytics.visits", "result", "dropped").increment();
    }
  }

  @Override
  public void publishAndAwait(AnalyticsEvent event) {
    KafkaProducer<String, String> client = producer;
    if (client == null) {
      throw new IllegalStateException("分析投递尚未建立 Kafka 连接");
    }
    try {
      client.send(record(event)).get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("分析事件投递被中断");
    } catch (Exception failure) {
      throw new IllegalStateException("Kafka 未确认分析事件", failure);
    }
  }

  private void sendVisit(AnalyticsEvent event) {
    KafkaProducer<String, String> client = producer;
    if (client == null) {
      metrics.counter("aiplatform.analytics.visits", "result", "dropped").increment();
      return;
    }
    for (int attempt = 1; attempt <= properties.getVisitAttempts(); attempt++) {
      try {
        client.send(record(event)).get(properties.getSendTimeoutMs(), TimeUnit.MILLISECONDS);
        metrics.counter("aiplatform.analytics.visits", "result", "sent").increment();
        return;
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        metrics.counter("aiplatform.analytics.visits", "result", "dropped").increment();
        return;
      } catch (Exception failure) {
        // 有界等待后重试；多次失败即丢弃并计数，不阻塞目录读取。
      }
    }
    metrics.counter("aiplatform.analytics.visits", "result", "dropped").increment();
  }

  private ProducerRecord<String, String> record(AnalyticsEvent event) {
    return new ProducerRecord<>(properties.getTopic(),
        String.valueOf(event.getApplicationId()), codec.encode(event));
  }

  private Properties producerProperties() {
    Properties properties = new Properties();
    properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, this.properties.getBootstrapServers());
    properties.put(ProducerConfig.CLIENT_ID_CONFIG, this.properties.getClientId() + "-producer");
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    properties.put(ProducerConfig.ACKS_CONFIG, "all");
    properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
    properties.put(ProducerConfig.LINGER_MS_CONFIG, "10");
    properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");
    properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
    properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "15000");
    properties.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "8388608");
    return properties;
  }

  private Properties consumerProperties() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.properties.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, this.properties.getGroup());
    properties.put(ConsumerConfig.CLIENT_ID_CONFIG, this.properties.getClientId() + "-consumer");
    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
        String.valueOf(this.properties.getPollRecords()));
    properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
    properties.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000");
    properties.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
    properties.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, "500");
    return properties;
  }

  private void consumeLoop() {
    try (KafkaConsumer<String, String> client = new KafkaConsumer<>(consumerProperties())) {
      consumer = client;
      client.subscribe(Collections.singleton(properties.getTopic()), new RebalanceListener(client));
      consuming.set(1);
      while (running) {
        ConsumerRecords<String, String> batch;
        try {
          batch = client.poll(Duration.ofMillis(properties.getPollTimeoutMs()));
        } catch (WakeupException wakeup) {
          break;
        } catch (RuntimeException failure) {
          log.warn("分析消费者轮询失败，type={}", failure.getClass().getSimpleName());
          sleep(properties.getRetryBackoffMs());
          continue;
        }
        updateLag(client);
        if (!batch.isEmpty()) {
          processBatch(client, batch);
        }
      }
    } catch (RuntimeException failure) {
      log.error("分析消费者退出，type={}", failure.getClass().getSimpleName());
    } finally {
      consuming.set(0);
    }
  }

  /** 顺序处理；单条失败时提交已落库位点并回卷，未提交的记录重放不会重复计数。 */
  private void processBatch(
      KafkaConsumer<String, String> client, ConsumerRecords<String, String> batch) {
    Map<TopicPartition, OffsetAndMetadata> processed = new HashMap<>();
    TopicPartition failedPartition = null;
    for (ConsumerRecord<String, String> record : batch) {
      AnalyticsEvent event = null;
      try {
        event = codec.decode(record.value());
        aggregation.accept(event);
        outcome(event, "accepted");
      } catch (AnalyticsMalformedException | AnalyticsConflictException invalid) {
        try {
          quarantine(record, invalid);
          outcome(event, "quarantined");
        } catch (RuntimeException storeFailure) {
          log.warn("分析异常消息隔离失败，partition={}，offset={}",
              record.partition(), record.offset());
          failedPartition = new TopicPartition(record.topic(), record.partition());
          break;
        }
      } catch (RuntimeException failure) {
        outcome(event, "failed");
        log.warn("分析消息处理失败，partition={}，offset={}，type={}",
            record.partition(), record.offset(), failure.getClass().getSimpleName());
        failedPartition = new TopicPartition(record.topic(), record.partition());
        break;
      }
      processed.put(new TopicPartition(record.topic(), record.partition()),
          new OffsetAndMetadata(record.offset() + 1));
    }
    boolean committedAll = commit(client, processed);
    if (committedAll) {
      committed.putAll(processed);
    }
    if (failedPartition != null) {
      rewind(client, batch, processed, committedAll);
      sleep(properties.getRetryBackoffMs());
    }
  }

  /** 只有已经提交数据库效果的位点才会被提交。 */
  private boolean commit(
      KafkaConsumer<String, String> client, Map<TopicPartition, OffsetAndMetadata> offsets) {
    if (offsets.isEmpty()) {
      return false;
    }
    try {
      client.commitSync(offsets);
      return true;
    } catch (RuntimeException failure) {
      log.warn("分析位点提交失败，等待幂等重放，type={}", failure.getClass().getSimpleName());
      return false;
    }
  }

  private void rewind(
      KafkaConsumer<String, String> client,
      ConsumerRecords<String, String> batch,
      Map<TopicPartition, OffsetAndMetadata> processed,
      boolean committedAll) {
    for (TopicPartition partition : batch.partitions()) {
      OffsetAndMetadata done = committedAll ? processed.get(partition) : null;
      long position = done != null ? done.offset() : batch.records(partition).get(0).offset();
      client.seek(partition, position);
    }
  }

  private void quarantine(ConsumerRecord<String, String> record, RuntimeException failure) {
    failures.insertFailure(record.topic(), record.partition(), record.offset(),
        FailureMessages.payload(record.value()), FailureMessages.summarize(failure));
  }

  private void outcome(AnalyticsEvent event, String result) {
    String action = event == null || event.getAction() == null
        ? "unknown" : event.getAction().name();
    metrics.counter(EVENT_METRIC, "action", action, "outcome", result).increment();
  }

  private void updateLag(KafkaConsumer<String, String> client) {
    for (Map.Entry<MetricName, ? extends Metric> entry : client.metrics().entrySet()) {
      if ("records-lag-max".equals(entry.getKey().name())
          && entry.getValue().metricValue() instanceof Number) {
        double value = ((Number) entry.getValue().metricValue()).doubleValue();
        if (Double.isFinite(value) && value >= 0) {
          lag.set((long) value);
        }
      }
    }
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(Math.max(1, millis));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  @PreDestroy
  public synchronized void stop() {
    if (!started) {
      return;
    }
    started = false;
    running = false;
    KafkaConsumer<String, String> active = consumer;
    if (active != null) {
      active.wakeup();
    }
    if (consumerThread != null) {
      try {
        consumerThread.join(5000);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
    visits.shutdownNow();
    KafkaProducer<String, String> sender = producer;
    producer = null;
    if (sender != null) {
      try {
        sender.close(Duration.ofSeconds(5));
      } catch (RuntimeException failure) {
        log.warn("分析生产者关闭失败，type={}", failure.getClass().getSimpleName());
      }
    }
  }

  /** 再均衡时只提交已落库位点，撤销后由组内其他实例从提交位置继续。 */
  private final class RebalanceListener implements ConsumerRebalanceListener {
    private final KafkaConsumer<String, String> client;

    private RebalanceListener(KafkaConsumer<String, String> client) {
      this.client = client;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
      Map<TopicPartition, OffsetAndMetadata> subset = new HashMap<>();
      for (TopicPartition partition : partitions) {
        OffsetAndMetadata offset = committed.get(partition);
        if (offset != null) {
          subset.put(partition, offset);
        }
      }
      commit(client, subset);
      for (TopicPartition partition : partitions) {
        committed.remove(partition);
      }
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
      log.info("分析消费者已接管分区，count={}", partitions.size());
    }
  }
}
