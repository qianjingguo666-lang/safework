#!/usr/bin/env bash
# 新服务器初始化（Ubuntu 22.04 / 24.04），只需要运行一次：
#   sudo bash deploy/server-setup.sh
set -e

echo "== 1. 时区设为北京时间 =="
timedatectl set-timezone Asia/Shanghai || true

echo "== 2. 添加 2G 交换空间（小内存服务器编译时防止内存不足被杀进程） =="
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
else
  echo "已有交换空间，跳过"
fi

echo "== 3. 安装 Docker =="
if ! command -v docker >/dev/null 2>&1; then
  # 国内服务器使用阿里云镜像安装；香港 / 海外服务器同样可用
  curl -fsSL https://get.docker.com | sh -s -- --mirror Aliyun
fi
systemctl enable --now docker

echo "== 4. 检查 =="
docker --version
docker compose version
free -h
echo "初始化完成"
