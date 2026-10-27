#!/bin/sh
set -eu

: "${APP_MQ_TOPIC:?APP_MQ_TOPIC is required}"
NAMESERVER="${APP_MQ_NAMESERVER:-mq-nameserver:9876}"
BROKER="${APP_MQ_BROKER:-rocketmq:10911}"

# 关闭自动建 Topic 后，由该一次性容器显式创建环境专属 Topic。
sh mqadmin updateTopic -n "$NAMESERVER" -b "$BROKER" -t "$APP_MQ_TOPIC" -r 4 -w 4
