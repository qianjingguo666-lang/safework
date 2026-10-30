#!/usr/bin/env bash
# 启动 / 发布新版本：在发布包目录（/opt/safework）运行  bash start.sh
set -e
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "缺少 .env：请先执行 cp .env.example .env，再用 nano .env 修改密码"
  exit 1
fi
if grep -q 'ChangeMe123' .env; then
  echo ".env 里还是默认密码（ChangeMe123…），请先修改 MYSQL_ROOT_PASSWORD 和 REDIS_PASSWORD"
  exit 1
fi
for f in backend/app.jar web/dist/index.html; do
  [ -f "$f" ] || { echo "缺少 $f，发布包不完整，请重新上传"; exit 1; }
done
docker compose up -d --build
# 清理旧镜像，避免磁盘被占满
docker image prune -f >/dev/null
echo
echo "== 容器状态（backend 第一次启动要建表，约 1 分钟）=="
docker compose ps
echo
echo "查看后端日志：docker compose logs -f --tail=100 backend（按 Ctrl+C 退出查看，不会停止服务）"
