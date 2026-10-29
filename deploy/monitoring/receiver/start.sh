#!/bin/sh
set -eu
# 隔离的告警验证接收器；只监听容器网络与本机回环映射。
exec /bin/busybox nc -lk -p 8099 -e /receiver/handler
