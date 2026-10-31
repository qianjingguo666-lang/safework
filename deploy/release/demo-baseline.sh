#!/usr/bin/env bash
# 演示站：把现在的数据保存为“干净的演示数据”，之后每天的 demo-reset.sh 都恢复成这个样子
#   bash demo-baseline.sh
# 什么时候运行：第一次部署、改好平台管理员密码、准备好演示数据（比如上传了知识库文件）之后
set -e
cd "$(dirname "$0")"
source .env
mkdir -p demo
echo "== 1. 备份数据库 =="
docker compose exec -T -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql mysqldump -uroot --single-transaction --routines \
  --default-character-set=utf8mb4 safework | gzip > demo/baseline.sql.gz.tmp
mv demo/baseline.sql.gz.tmp demo/baseline.sql.gz
echo "== 2. 备份上传的文件（附件、知识库原文件） =="
docker compose run --rm --no-deps -T -v "$PWD/demo:/backup" --entrypoint sh backend \
  -c 'mkdir -p /app/data && cd /app/data && tar -czf /backup/uploads.tar.gz .'
ls -lh demo/baseline.sql.gz demo/uploads.tar.gz
echo "已保存演示基线。每天自动恢复：crontab -e 后加一行"
echo "  0 4 * * * cd $PWD && bash demo-reset.sh >> demo/reset.log 2>&1"
