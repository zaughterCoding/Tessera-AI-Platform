#!/bin/sh
set -eu

: "${CANAL_DESTINATION:?}"
: "${CANAL_SCHEMA:?}"
: "${CANAL_DB_HOST:?}"
: "${CANAL_DB_PORT:?}"
: "${CANAL_DB_USERNAME:?}"
: "${CANAL_DB_PASSWORD:?}"
: "${CANAL_CLIENT_USER:?}"
: "${CANAL_SERVER_PASSWORD:?}"
: "${CANAL_SLAVE_ID:?}"

case "$CANAL_SCHEMA$CANAL_DB_USERNAME$CANAL_CLIENT_USER" in
  *[!a-zA-Z0-9_]*) echo 'Canal schema 或账户名包含不支持的字符。' >&2; exit 1 ;;
esac
case "$CANAL_DESTINATION$CANAL_DB_HOST" in
  *[!a-zA-Z0-9_.-]*) echo 'Canal destination 或主机名包含不支持的字符。' >&2; exit 1 ;;
esac
case "$CANAL_SLAVE_ID" in
  *[!0-9]*) echo 'Canal slave id 必须为数字。' >&2; exit 1 ;;
esac
case "$CANAL_DB_PORT" in
  *[!0-9]*) echo 'Canal 数据库端口必须为数字。' >&2; exit 1 ;;
esac
case "$CANAL_DB_PASSWORD$CANAL_SERVER_PASSWORD" in
  *[!a-zA-Z0-9_-]*) echo 'Canal 密码包含不支持的字符。' >&2; exit 1 ;;
esac
if [ "${#CANAL_DB_PASSWORD}" -lt 32 ] || [ "${#CANAL_SERVER_PASSWORD}" -lt 40 ]; then
  echo 'Canal 密码长度不满足要求。' >&2
  exit 1
fi

CONF=/home/admin/canal-server/conf
DEST_DIR="$CONF/$CANAL_DESTINATION"
# 镜像只在示例目录内生成 instance.properties；显式生成并保留目的地目录中的元数据。
rm -rf "$CONF/example"
mkdir -p "$DEST_DIR"
sed \
  -e "s|@CANAL_DESTINATION@|$CANAL_DESTINATION|g" \
  -e "s|@CANAL_SCHEMA@|$CANAL_SCHEMA|g" \
  -e "s|@CANAL_DB_HOST@|$CANAL_DB_HOST|g" \
  -e "s|@CANAL_DB_PORT@|$CANAL_DB_PORT|g" \
  -e "s|@CANAL_DB_USERNAME@|$CANAL_DB_USERNAME|g" \
  -e "s|@CANAL_DB_PASSWORD@|$CANAL_DB_PASSWORD|g" \
  -e "s|@CANAL_SLAVE_ID@|$CANAL_SLAVE_ID|g" \
  /home/admin/instance.properties.template > "$DEST_DIR/instance.properties.tmp"
mv "$DEST_DIR/instance.properties.tmp" "$DEST_DIR/instance.properties"

# 后续同名属性覆盖镜像默认值；禁止在 binlog 缺失时跳到最新位置。
{
  echo ''
  echo "canal.destinations = $CANAL_DESTINATION"
  echo 'canal.auto.scan = false'
  echo 'canal.auto.reset.latest.pos.mode = false'
  echo "canal.user = $CANAL_CLIENT_USER"
  echo "canal.passwd = $CANAL_SERVER_PASSWORD"
} >> "$CONF/canal.properties"

chown -R admin:admin "$DEST_DIR" "$CONF/canal.properties"
exec /home/admin/app.sh
