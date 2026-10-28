package com.aiplatform.campaign;

import com.aiplatform.campaign.entity.CampaignDeadLetter;
import com.aiplatform.campaign.entity.CampaignReservation;
import com.aiplatform.campaign.enums.ReservationState;
import com.aiplatform.campaign.mapper.CampaignDeadLetterMapper;
import com.aiplatform.campaign.mapper.CampaignReservationMapper;
import com.aiplatform.campaign.service.ICampaignFulfillmentService;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 消费组只负责定位预约；建单事实由数据库行锁和幂等写入决定，确认前绝不声称成功。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.campaign.consumer-enabled", havingValue = "true",
    matchIfMissing = true)
public class CampaignStreamConsumer implements SmartLifecycle {
  private static final int PAYLOAD_LIMIT = 2048;
  private static final int REASON_LIMIT = 255;
  private static final long ERROR_BACKOFF_MILLIS = 1000L;
  private final StringRedisTemplate redis;
  private final CampaignKeys keys;
  private final ICampaignFulfillmentService fulfillment;
  private final CampaignReservationMapper reservations;
  private final CampaignDeadLetterMapper letters;
  private final MeterRegistry metrics;
  private final int batch;
  private final int blockMillis;
  private final int claimIdleMillis;
  private final int maxDeliveries;
  private final boolean outboxMode;
  private final String consumerName;
  private final StreamOperations<String, String, String> streams;
  private volatile boolean running;
  private volatile boolean groupReady;
  private Thread worker;

  public CampaignStreamConsumer(
      StringRedisTemplate redis,
      CampaignKeys keys,
      ICampaignFulfillmentService fulfillment,
      CampaignReservationMapper reservations,
      CampaignDeadLetterMapper letters,
      MeterRegistry metrics,
      @Value("${app.namespace}") String namespace,
      @Value("${app.campaign.fulfillment-mode:stream}") String mode,
      @Value("${app.campaign.consumer-batch:10}") int batch,
      @Value("${app.campaign.consumer-block-ms:1000}") int blockMillis,
      @Value("${app.campaign.claim-idle-ms:5000}") int claimIdleMillis,
      @Value("${app.campaign.max-deliveries:5}") int maxDeliveries) {
    this.redis = redis;
    this.keys = keys;
    this.fulfillment = fulfillment;
    this.reservations = reservations;
    this.letters = letters;
    this.metrics = metrics;
    this.batch = batch;
    this.blockMillis = blockMillis;
    this.claimIdleMillis = claimIdleMillis;
    this.maxDeliveries = maxDeliveries;
    this.outboxMode = "outbox".equals(mode);
    this.consumerName = namespace + "-campaign";
    this.streams = redis.opsForStream();
  }

  @Override
  public void start() {
    if (running) {
      return;
    }
    running = true;
    worker = new Thread(this::loop, "campaign-stream-consumer");
    worker.setDaemon(true);
    worker.start();
  }

  @Override
  public void stop() {
    running = false;
    Thread current = worker;
    worker = null;
    if (current != null) {
      current.interrupt();
      try {
        current.join(5000L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void loop() {
    while (running) {
      try {
        if (!groupReady) {
          ensureGroup();
        }
        if (running) {
          reclaim();
        }
        if (running) {
          readNew();
        }
      } catch (RuntimeException failure) {
        if (isMissingGroup(failure)) {
          groupReady = false;
        }
        log.warn("活动消费循环失败，type={}，detail={}",
            failure.getClass().getSimpleName(), failure.getMessage());
        sleep();
      }
    }
  }

  private void ensureGroup() {
    try {
      redis.execute((RedisCallback<Object>) connection -> connection.execute(
          "XGROUP",
          bytes("CREATE"), bytes(keys.stream()), bytes(keys.group()),
          bytes("0"), bytes("MKSTREAM")));
      groupReady = true;
    } catch (RuntimeException failure) {
      if (isBusyGroup(failure)) {
        groupReady = true;
        return;
      }
      throw failure;
    }
  }

  @SuppressWarnings("unchecked")
  private void readNew() {
    List<MapRecord<String, String, String>> records = streams.read(
        Consumer.from(keys.group(), consumerName),
        StreamReadOptions.empty().count(batch).block(Duration.ofMillis(blockMillis)),
        StreamOffset.create(keys.stream(), ReadOffset.lastConsumed()));
    if (records == null) {
      return;
    }
    for (MapRecord<String, String, String> record : records) {
      handle(record.getId(), record.getValue());
    }
  }

  private void reclaim() {
    PendingMessages pending = streams.pending(
        keys.stream(), keys.group(), Range.unbounded(), batch);
    if (pending == null || pending.isEmpty()) {
      return;
    }
    List<RecordId> claimable = new ArrayList<>();
    for (PendingMessage message : pending) {
      if (message.getElapsedTimeSinceLastDelivery().toMillis() < claimIdleMillis) {
        continue;
      }
      if (message.getTotalDeliveryCount() >= maxDeliveries) {
        quarantineExhausted(message.getId());
        continue;
      }
      claimable.add(message.getId());
    }
    if (claimable.isEmpty()) {
      return;
    }
    // Spring Data Redis 2.6 未提供 claim，直接使用 XCLAIM JUSTID 接管后再取消息体。
    List<RecordId> claimed = claim(claimable);
    for (RecordId id : claimed) {
      handle(id, fieldsAt(id));
    }
  }

  private List<RecordId> claim(List<RecordId> ids) {
    List<byte[]> args = new ArrayList<>();
    args.add(bytes(keys.stream()));
    args.add(bytes(keys.group()));
    args.add(bytes(consumerName));
    args.add(bytes(String.valueOf(claimIdleMillis)));
    for (RecordId id : ids) {
      args.add(bytes(id.getValue()));
    }
    args.add(bytes("JUSTID"));
    Object reply = redis.execute((RedisCallback<Object>) connection ->
        connection.execute("XCLAIM", args.toArray(new byte[0][])));
    if (reply == null) {
      log.warn("活动消息接管未返回结果");
      return new ArrayList<>();
    }
    // Lettuce 对 JUSTID 结果按单值字节返回，不能依赖返回列表；接管后按原 Pending 集合处理。
    return ids;
  }

  private void quarantineExhausted(RecordId id) {
    Map<String, String> fields = fieldsAt(id);
    CampaignMessage message = parseQuietly(fields);
    if (!quarantine(id.getValue(), message, fields, "消息投递次数超限")) {
      return;
    }
    acknowledge(id);
    metrics.counter("aiplatform.campaign.messages", "outcome", "exhausted").increment();
  }

  private void handle(RecordId id, Map<String, String> source) {
    Map<String, String> fields = new LinkedHashMap<>(source);
    CampaignMessage message = parseQuietly(fields);
    if (message == null) {
      if (quarantine(id.getValue(), null, fields, "预约消息身份不可信")) {
        acknowledge(id);
      }
      return;
    }
    RegisterOutcome outcome;
    try {
      outcome = fulfillment.register(message, outboxMode);
    } catch (RuntimeException failure) {
      // 登记失败不确认消息，留待 Pending 接管或人工复核。
      log.warn("活动消息登记失败，orderId={}，type={}",
          message.getOrderId(), failure.getClass().getSimpleName());
      metrics.counter("aiplatform.campaign.messages", "outcome", "register-retry").increment();
      return;
    }
    if (outcome == RegisterOutcome.SUCCESS || outcome == RegisterOutcome.FAILED) {
      acknowledge(id);
      return;
    }
    if (outcome == RegisterOutcome.INVALID) {
      if (quarantine(id.getValue(), message, fields, "预约身份与数据库不一致")) {
        acknowledge(id);
      }
      return;
    }
    if (outcome == RegisterOutcome.REVIEW || outcome == RegisterOutcome.STALE) {
      if (quarantine(id.getValue(), message, fields, "预约需要人工复核")) {
        acknowledge(id);
      }
      return;
    }
    if (outboxMode) {
      // 交接任务已与登记同事务提交，确认消息不会丢失建单责任。
      acknowledge(id);
      metrics.counter("aiplatform.campaign.messages", "outcome", "handoff").increment();
      return;
    }
    fulfill(id, message, fields);
  }

  private void fulfill(RecordId id, CampaignMessage message, Map<String, String> fields) {
    try {
      fulfillment.fulfill(message.getOrderId());
      acknowledge(id);
      metrics.counter("aiplatform.campaign.messages", "outcome", "fulfilled").increment();
    } catch (RuntimeException failure) {
      int attempts;
      try {
        attempts = fulfillment.recordFailure(message.getOrderId(), failure);
      } catch (RuntimeException recordFailure) {
        log.warn("活动建单失败记录未写入，orderId={}，type={}",
            message.getOrderId(), recordFailure.getClass().getSimpleName());
        return;
      }
      CampaignReservation reservation =
          reservations.selectReservation(message.getOrderId());
      if (reservation != null
          && !ReservationState.PROCESSING.name().equals(reservation.getState())) {
        // 另一执行者已给出终态，按数据库事实确认或隔离。
        if (ReservationState.REVIEW.name().equals(reservation.getState())) {
          if (quarantine(id.getValue(), message, fields, "预约需要人工复核")) {
            acknowledge(id);
          }
        } else {
          acknowledge(id);
        }
        return;
      }
      if (attempts >= maxDeliveries) {
        try {
          fulfillment.markReview(message.getOrderId(), "建单重试次数超限");
        } catch (RuntimeException markFailure) {
          log.warn("活动预约隔离失败，orderId={}，type={}",
              message.getOrderId(), markFailure.getClass().getSimpleName());
          return;
        }
        if (quarantine(id.getValue(), message, fields, "建单重试次数超限")) {
          acknowledge(id);
        }
        metrics.counter("aiplatform.campaign.messages", "outcome", "quarantined").increment();
        return;
      }
      metrics.counter("aiplatform.campaign.messages", "outcome", "retry").increment();
      log.warn("活动建单失败，保留 Pending 等待接管，orderId={}，attempts={}",
          message.getOrderId(), attempts);
    }
  }

  private void acknowledge(RecordId id) {
    Long acknowledged = streams.acknowledge(keys.stream(), keys.group(), id);
    if (acknowledged == null || acknowledged == 0L) {
      log.warn("活动消息确认未生效，recordId={}", id.getValue());
    }
  }

  private boolean quarantine(String recordId, CampaignMessage message,
      Map<String, String> fields, String reason) {
    CampaignDeadLetter letter = new CampaignDeadLetter();
    letter.setMessageId(recordId);
    letter.setCampaignId(message == null ? null : message.getCampaignId());
    letter.setOrderId(message == null ? null : message.getOrderId());
    letter.setPayload(payload(fields));
    letter.setReason(truncate(reason));
    try {
      letters.insertIgnore(letter);
      return true;
    } catch (RuntimeException failure) {
      // 死信写入失败时不确认消息，避免不可恢复地丢消息。
      log.warn("活动死信写入失败，recordId={}，type={}",
          recordId, failure.getClass().getSimpleName());
      return false;
    }
  }

  private Map<String, String> fieldsAt(RecordId id) {
    try {
      List<MapRecord<String, String, String>> records = streams.range(
          keys.stream(), Range.closed(id.getValue(), id.getValue()));
      if (records == null || records.isEmpty()) {
        return new LinkedHashMap<>();
      }
      return new LinkedHashMap<>(records.get(0).getValue());
    } catch (RuntimeException failure) {
      return new LinkedHashMap<>();
    }
  }

  private CampaignMessage parseQuietly(Map<String, String> fields) {
    try {
      return CampaignMessage.parse(fields);
    } catch (RuntimeException invalid) {
      return null;
    }
  }

  private String payload(Map<String, String> fields) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      if (builder.length() > 0) {
        builder.append('&');
      }
      builder.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return truncate(builder.toString(), PAYLOAD_LIMIT);
  }

  private String truncate(String value) {
    return truncate(value, REASON_LIMIT);
  }

  private String truncate(String value, int limit) {
    if (value == null) {
      return "";
    }
    return value.length() <= limit ? value : value.substring(0, limit);
  }

  private boolean isBusyGroup(RuntimeException failure) {
    return messageContains(failure, "BUSYGROUP");
  }

  private boolean isMissingGroup(RuntimeException failure) {
    return messageContains(failure, "NOGROUP");
  }

  private boolean messageContains(Throwable failure, String token) {
    Throwable current = failure;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.contains(token)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private void sleep() {
    try {
      Thread.sleep(ERROR_BACKOFF_MILLIS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      running = false;
    }
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
