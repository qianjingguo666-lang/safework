#!/usr/bin/env bash
# 备份数据库到 backups/，保留最近 7 天
#   手动运行：bash backup.sh
#   每天凌晨 3 点自动运行：crontab -e 后加一行
#   0 3 * * * cd /opt/safework && bash backup.sh >> backups/backup.log 2>&1
set -e
cd "$(dirname "$0")"
# 只读取需要的配置，不用 source .env：.env 里 JAVA_OPTS 这类带空格的值会被 bash 当成命令执行
env_get() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -E 's/^["'"'"'](.*)["'"'"']$/\1/'; }
MYSQL_ROOT_PASSWORD=$(env_get MYSQL_ROOT_PASSWORD)
mkdir -p backups
FILE="backups/safework-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose exec -T mysql \
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --default-character-set=utf8mb4 safework | gzip > "$FILE"
find backups -name 'safework-*.sql.gz' -mtime +7 -delete
echo "备份完成：$FILE"
