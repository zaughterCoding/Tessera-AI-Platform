#!/usr/bin/env sh
# 源项目 GEO 能力的隔离示例，不属于 AI 应用目录主流程。
# 依赖 redis-cli；通过 REDIS_HOST、REDIS_PORT、REDIS_PASSWORD 连接环境。
set -eu

: "${REDIS_HOST:=127.0.0.1}"
: "${REDIS_PORT:=6379}"
: "${GEO_KEY:=legacy:geo:demo}"

if ! command -v redis-cli >/dev/null 2>&1; then
  echo "未找到 redis-cli。" >&2
  exit 1
fi

redis_cli() {
  if [ -n "${REDIS_PASSWORD:-}" ]; then
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" \
      --no-auth-warning -a "$REDIS_PASSWORD" "$@"
  else
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" "$@"
  fi
}

cleanup() {
  redis_cli DEL "$GEO_KEY" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# 使用完全虚构的坐标演示距离筛选，不包含真实商家或地址。
redis_cli GEOADD "$GEO_KEY" \
  116.404000 39.915000 "demo-east" \
  116.410000 39.916000 "demo-near" \
  116.500000 39.920000 "demo-far" >/dev/null

echo "5 公里内的示例点位："
redis_cli GEOSEARCH "$GEO_KEY" \
  FROMLONLAT 116.404000 39.915000 BYRADIUS 5 km ASC \
  WITHDIST WITHCOORD
