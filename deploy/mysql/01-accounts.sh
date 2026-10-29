#!/bin/sh
set -eu

case "$MYSQL_DATABASE" in
  ai_creation_*) ;;
  *) echo '数据库名必须以 ai_creation_ 开头。' >&2; exit 1 ;;
esac
case "$MYSQL_DATABASE" in
  *[!a-z0-9_]*) echo '数据库名只允许小写字母、数字和下划线。' >&2; exit 1 ;;
esac

# 初始化脚本只接受环境生成器的安全字符集，避免密码被解释为 SQL。
case "$APP_DB_PASSWORD$SCHEMA_DB_PASSWORD" in
  *[!a-zA-Z0-9_-]*) echo '数据库密码包含不支持的字符。' >&2; exit 1 ;;
esac
if [ "${#APP_DB_PASSWORD}" -lt 32 ] || [ "${#SCHEMA_DB_PASSWORD}" -lt 32 ]; then
  echo '数据库密码至少需要 32 个字符。' >&2; exit 1
fi

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
CREATE USER 'ai_schema'@'%' IDENTIFIED BY '$SCHEMA_DB_PASSWORD';
CREATE USER 'ai_app'@'%' IDENTIFIED BY '$APP_DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$MYSQL_DATABASE\`.* TO 'ai_schema'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`$MYSQL_DATABASE\`.* TO 'ai_app'@'%';
SQL
