#!/usr/bin/env bash
# 服务器初始化（Ubuntu 22.04 / 24.04，国内服务器），只需要运行一次：
#   bash server-init.sh                         使用公共镜像加速
#   bash server-init.sh https://xxxx.mirror.aliyuncs.com   同时使用阿里云专属镜像加速（推荐，阿里云服务器）
set -e
ALIYUN_MIRROR="$1"

echo "== 1. 时区设为北京时间 =="
timedatectl set-timezone Asia/Shanghai || true

echo "== 2. 添加 2G 交换空间（小内存服务器防止内存不足时进程被杀） =="
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
else
  echo "已有交换空间，跳过"
fi

echo "== 3. 安装 Docker =="
if ! command -v docker >/dev/null 2>&1; then
  apt-get update
  apt-get install -y docker.io docker-compose-v2
fi
if ! docker compose version >/dev/null 2>&1; then
  apt-get update
  apt-get install -y docker-compose-v2 || apt-get install -y docker-compose-plugin
fi
systemctl enable --now docker

echo "== 4. 配置镜像加速（国内服务器连不上 Docker Hub）和容器日志大小限制 =="
MIRRORS='"https://docker.m.daocloud.io", "https://docker.1ms.run"'
if [ -n "$ALIYUN_MIRROR" ]; then
  MIRRORS="\"$ALIYUN_MIRROR\", $MIRRORS"
fi
mkdir -p /etc/docker
if [ -f /etc/docker/daemon.json ]; then
  cp /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%Y%m%d%H%M%S)"
fi
cat > /etc/docker/daemon.json <<JSON
{
  "registry-mirrors": [$MIRRORS],
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
JSON
systemctl daemon-reload
systemctl restart docker

echo "== 5. 预先拉取需要的镜像 =="
for img in mysql:8.0 redis:7-alpine eclipse-temurin:17-jre nginx:1.27-alpine; do
  echo "-- $img"
  docker pull "$img" || { echo "拉取 $img 失败：镜像加速地址不可用，把上面的输出发给 Claude"; exit 1; }
done

echo "== 6. 检查 =="
docker --version
docker compose version
free -h
echo "初始化完成"
