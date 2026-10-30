package com.aiplatform.messaging;

import com.aiplatform.messaging.service.IBusinessEventService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 真实 Broker 传输；Topic 与消费组使用环境前缀，消息身份仍由数据库任务核对。 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.messaging.transport", havingValue = "rocketmq")
public class RocketMqTransport implements EventPublisher, SmartLifecycle {
  private static final long MAX_DELAY_MILLIS = 24L * 60 * 60 * 1000;
  private final IBusinessEventService consumer;
  private final ObjectMapper json;
  private final String nameServer;
  private final String topic;
  private final String groupPrefix;
  private final List<DefaultMQPushConsumer> consumers = new ArrayList<>();
  private DefaultMQProducer producer;
  private volatile boolean running;

  public RocketMqTransport(
      IBusinessEventService consumer,
      ObjectMapper json,
      @Value("${app.messaging.name-server:}") String nameServer,
      @Value("${app.messaging.topic:}") String topic,
      @Value("${app.messaging.consumer-group-prefix:}") String groupPrefix) {
    this.consumer = consumer;
    this.json = json;
    this.nameServer = nameServer;
    this.topic = topic;
    this.groupPrefix = groupPrefix;
  }

  @Override
  public synchronized void start() {
    if (running) {
      return;
    }
    requireConfigured();
    try {
      startProducer();
      for (BusinessEventType type : BusinessEventType.values()) {
        startConsumer(type);
      }
      running = true;
      log.info("RocketMQ 传输已启动，topic={}，groups={}", topic, groupPrefix);
    } catch (Exception failure) {
      stop();
      throw new IllegalStateException("RocketMQ 传输启动失败", failure);
    }
  }

  private void requireConfigured() {
    if (nameServer.isEmpty() || topic.isEmpty() || groupPrefix.isEmpty()) {
      throw new IllegalStateException("RocketMQ 地址、Topic 和消费组前缀必须配置");
    }
  }

  private void startProducer() throws Exception {
    producer = new DefaultMQProducer(groupPrefix + "-outbox-producer");
    producer.setNamesrvAddr(nameServer);
    producer.setInstanceName("outbox-" + UUID.randomUUID());
    producer.setVipChannelEnabled(false);
    producer.setSendMsgTimeout(3000);
    producer.start();
  }

  private void startConsumer(BusinessEventType type) throws Exception {
    String group = groupPrefix + "-" + type.consumerGroup();
    DefaultMQPushConsumer client = new DefaultMQPushConsumer(group);
    client.setNamesrvAddr(nameServer);
    client.setInstanceName(type.consumerGroup() + "-" + UUID.randomUUID());
    client.setVipChannelEnabled(false);
    client.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
    client.setConsumeMessageBatchMaxSize(1);
    client.setConsumeThreadMin(2);
    client.setConsumeThreadMax(4);
    client.setMaxReconsumeTimes(16);
    client.subscribe(topic, "*");
    String subscribedGroup = type.consumerGroup();
    client.registerMessageListener((MessageListenerConcurrently) (messages, context) ->
        handle(messages, subscribedGroup));
    client.start();
    consumers.add(client);
  }

  private ConsumeConcurrentlyStatus handle(
      List<MessageExt> messages, String subscribedGroup) {
    for (MessageExt message : messages) {
      String payload = new String(message.getBody(), StandardCharsets.UTF_8);
      BusinessEvent event = null;
      try {
        event = json.readValue(payload, BusinessEvent.class);
        // 每个逻辑消费组都订阅 Topic；仅由事件类型对应的组处理，避免其他组把
        // 合法消息误记为伪造消息。未知类型仍按当前组隔离，保留伪造排查能力。
        if (belongsToOtherGroup(event, subscribedGroup)) {
          continue;
        }
        consumer.consume(event, subscribedGroup);
      } catch (Exception failure) {
        try {
          boolean acknowledged = consumer.recordConsumptionFailure(
              event, subscribedGroup, message.getMsgId(), payload, failure,
              message.getReconsumeTimes() + 1);
          if (acknowledged) {
            continue;
          }
        } catch (RuntimeException storageFailure) {
          log.warn("消息失败记录写入失败，保留 Broker 重投，type={}",
              storageFailure.getClass().getName());
        }
        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
      }
    }
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
  }

  private boolean belongsToOtherGroup(BusinessEvent event, String subscribedGroup) {
    if (event == null || event.getKind() == null) {
      return false;
    }
    try {
      return !BusinessEventType.from(event.getKind()).consumerGroup().equals(subscribedGroup);
    } catch (IllegalArgumentException unknownKind) {
      return false;
    }
  }

  @Override
  public void publish(BusinessEvent event) {
    if (!running || producer == null) {
      throw new IllegalStateException("RocketMQ 生产者未就绪");
    }
    try {
      Message message = new Message(topic, json.writeValueAsBytes(event));
      message.setKeys(String.valueOf(event.getEventId()));
      applyDelay(message, event);
      SendResult result = producer.send(message);
      if (result.getSendStatus() != SendStatus.SEND_OK) {
        throw new IllegalStateException("RocketMQ 未确认消息，status=" + result.getSendStatus());
      }
    } catch (Exception failure) {
      throw new IllegalStateException("RocketMQ 发送失败", failure);
    }
  }

  private void applyDelay(Message message, BusinessEvent event) {
    if (event.getDeliverAt() == null) {
      return;
    }
    long delay = event.getDeliverAt() - System.currentTimeMillis();
    if (delay <= 0) {
      return;
    }
    if (delay > MAX_DELAY_MILLIS) {
      throw new IllegalArgumentException("延迟投递超过 24 小时");
    }
    message.setDeliverTimeMs(event.getDeliverAt());
  }

  @Override
  public synchronized void stop() {
    running = false;
    for (DefaultMQPushConsumer client : consumers) {
      try {
        client.shutdown();
      } catch (RuntimeException failure) {
        log.warn("RocketMQ 消费者停止失败，type={}", failure.getClass().getName());
      }
    }
    consumers.clear();
    if (producer != null) {
      producer.shutdown();
      producer = null;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
