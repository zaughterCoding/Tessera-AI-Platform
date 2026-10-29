#!/bin/sh
set -eu

case "$MYSQL_DATABASE" in
  ai_creation_*) ;;
  *) echo '数据库名必须以 ai_creation_ 开头。' >&2; exit 1 ;;
esac
case "$MYSQL_DATABASE" in
  *[!a-z0-9_]*) echo '数据库名只允许小写字母、数字和下划线。' >&2; exit 1 ;;
esac

# Canal 使用独立复制账户，不复用应用账户；只授予读取目录库与复制所需权限。
case "$CANAL_DB_PASSWORD" in
  *[!a-zA-Z0-9_-]*) echo 'Canal 数据库密码包含不支持的字符。' >&2; exit 1 ;;
esac
if [ "${#CANAL_DB_PASSWORD}" -lt 32 ]; then
  echo 'Canal 数据库密码至少需要 32 个字符。' >&2; exit 1
fi

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE USER IF NOT EXISTS 'ai_canal'@'%'
  IDENTIFIED WITH mysql_native_password BY '$CANAL_DB_PASSWORD';
GRANT SELECT ON \`$MYSQL_DATABASE\`.* TO 'ai_canal'@'%';
-- Canal 启动时从 performance_schema 读取表元数据，只读权限即可。
GRANT SELECT ON \`performance_schema\`.* TO 'ai_canal'@'%';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'ai_canal'@'%';
SQL
