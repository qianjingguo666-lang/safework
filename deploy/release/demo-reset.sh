#!/usr/bin/env bash
# 演示站：把数据恢复成 demo-baseline.sh 保存的样子（数据库 + 上传的文件 + 清空 Redis）
#   手动运行：bash demo-reset.sh
#   每天凌晨 4 点自动运行：0 4 * * * cd /opt/safework && bash demo-reset.sh >> demo/reset.log 2>&1
# 大约停机 1 分钟：先停后端，恢复完再启动；启动时 Flyway 会自动执行基线之后新增的数据库版本
set -e
cd "$(dirname "$0")"
# 只读取需要的配置，不用 source .env：.env 里 JAVA_OPTS 这类带空格的值会被 bash 当成命令执行
env_get() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -E 's/^["'"'"'](.*)["'"'"']$/\1/'; }
MYSQL_ROOT_PASSWORD=$(env_get MYSQL_ROOT_PASSWORD)
REDIS_PASSWORD=$(env_get REDIS_PASSWORD)
WEB_PORT=$(env_get WEB_PORT)
[ -f demo/baseline.sql.gz ] || { echo "缺少 demo/baseline.sql.gz：先运行 bash demo-baseline.sh"; exit 1; }
# 密码用环境变量 MYSQL_PWD 传给容器，不会出现“命令行里写密码不安全”的警告
MYSQL="docker compose exec -T -e MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql mysql -uroot --default-character-set=utf8mb4"

echo "$(date '+%F %T') == 开始恢复演示数据 =="
docker compose stop backend

echo "-- 数据库"
$MYSQL -e "DROP DATABASE IF EXISTS safework; CREATE DATABASE safework DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
gunzip -c demo/baseline.sql.gz | $MYSQL safework
# 基线里的企业到期时间会慢慢变成过去，每次恢复都往后顺延，演示企业永远不会“到期停用”
$MYSQL safework -e "UPDATE sys_tenant SET expire_time = DATE_ADD(CURDATE(), INTERVAL 1 YEAR) WHERE tenant_code IN ('demo', 'test') AND expire_time IS NOT NULL;"

echo "-- 上传的文件"
if [ -f demo/uploads.tar.gz ]; then
  docker compose run --rm --no-deps -T -v "$PWD/demo:/backup" --entrypoint sh backend \
    -c 'mkdir -p /app/data && cd /app/data && find . -mindepth 1 -delete && tar -xzf /backup/uploads.tar.gz'
fi

echo "-- Redis（登录状态、验证码、限流计数，所有人需要重新登录）"
docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning FLUSHALL

docker compose start backend
for i in $(seq 1 60); do
  if curl -fs "http://localhost:${WEB_PORT:-80}/api/actuator/health" 2>/dev/null | grep -q UP; then
    echo "$(date '+%F %T') == 恢复完成，后端已启动 =="
    exit 0
  fi
  sleep 5
done
echo "$(date '+%F %T') 后端 5 分钟内没有启动成功，请查看：docker compose logs --tail=100 backend"
exit 1
