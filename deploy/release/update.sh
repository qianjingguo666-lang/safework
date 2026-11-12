#!/usr/bin/env bash
# 更新到新版本（一条命令完成：备份数据库 → 解压发布包 → 重新构建并启动）
#   先在自己电脑上运行 deploy\pack.ps1，再 scp 上传到 /root/safework-release.tar.gz，然后在服务器上运行：
#   cd /opt/safework && bash update.sh
#
# 整个脚本写在 main 函数里，最后一行才调用：解压时会覆盖 update.sh 自己，
# bash 已经把整个函数读进内存了，覆盖文件不影响正在执行的脚本。
main() {
  set -e
  cd "$(dirname "$0")"
  local pkg="${1:-/root/safework-release.tar.gz}"
  if [ ! -f "$pkg" ]; then
    echo "找不到发布包 $pkg：请先在电脑上运行 deploy\\pack.ps1，再用 scp 上传"
    exit 1
  fi
  local new_jar old_jar
  new_jar=$(tar -tvzf "$pkg" ./backend/app.jar | awk '{print $4, $5}')
  old_jar=$(date -r backend/app.jar '+%Y-%m-%d %H:%M' 2>/dev/null || echo '无')
  echo "== 发布包里的程序：$new_jar；正在运行的程序：$old_jar =="

  echo "== 1. 备份数据库 =="
  bash backup.sh </dev/null

  echo "== 2. 解压发布包 =="
  tar -xzf "$pkg" -C "$PWD"
  echo "已解压，backend/app.jar 时间：$(date -r backend/app.jar '+%Y-%m-%d %H:%M')"

  local renewal_hook="$PWD/renewal-hooks/deploy/safework-nginx-reload"
  if [ -f /etc/letsencrypt/live/qianjingguo.asia/fullchain.pem ] && [ -f "$renewal_hook" ]; then
    install -D -m 0755 "$renewal_hook" /etc/letsencrypt/renewal-hooks/deploy/safework-nginx-reload
    echo "已安装 Certbot 续期后 Nginx 重载钩子"
  fi

  echo "== 3. 重新构建并启动 =="
  bash start.sh </dev/null

  echo
  echo "== 4. 等后端启动（最多 3 分钟）=="
  local i
  for i in $(seq 1 36); do
    sleep 5
    if docker compose logs --since 5m backend 2>/dev/null | grep -q "Started SafeworkApplication"; then
      docker compose logs --since 5m backend | grep -E "Migrating|Started" || true
      echo "更新完成"
      return 0
    fi
  done
  echo "3 分钟内没看到启动成功，请查看：docker compose logs --tail=100 backend"
  exit 1
}
main "$@"
exit
