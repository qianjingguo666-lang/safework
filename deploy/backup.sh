#!/usr/bin/env bash
# 备份数据库到 backups/，保留最近 7 天
#   手动运行：bash deploy/backup.sh
#   每天凌晨 3 点自动运行：crontab -e 后加一行
#   0 3 * * * cd /opt/safework && bash deploy/backup.sh >> backups/backup.log 2>&1
set -e
cd "$(dirname "$0")/.."
source .env
mkdir -p backups
FILE="backups/safework-$(date +%Y%m%d-%H%M%S).sql.gz"
docker compose -f docker-compose.prod.yml exec -T mysql \
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --default-character-set=utf8mb4 safework | gzip > "$FILE"
find backups -name 'safework-*.sql.gz' -mtime +7 -delete
echo "备份完成：$FILE"
