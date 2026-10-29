#!/bin/sh
set -eu

: "${APP_ANALYTICS_TOPIC:?APP_ANALYTICS_TOPIC is required}"
BOOTSTRAP="${APP_KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"

# 分析主题与补数范围一致保留 7 天；分区固定 3 个，允许同一消费组内并行处理。
/opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create \
  --if-not-exists --topic "$APP_ANALYTICS_TOPIC" --partitions 3 --replication-factor 1 \
  --config retention.ms=604800000 \
  --config cleanup.policy=delete
