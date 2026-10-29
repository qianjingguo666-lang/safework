#!/usr/bin/env bash
# 发布新版本：在项目目录运行  bash deploy/update.sh
set -e
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then
  echo "缺少 .env，请先执行：cp .env.example .env 并修改密码"
  exit 1
fi
docker compose -f docker-compose.prod.yml up -d --build
# 清理旧镜像，避免磁盘被占满
docker image prune -f
docker compose -f docker-compose.prod.yml ps
