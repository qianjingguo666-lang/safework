# 部署说明

整套系统由 4 个容器组成，一条命令启动：

```
浏览器 ──80──▶ web（Nginx：前端页面 + 转发 /api）──▶ backend（Spring Boot）──▶ mysql / redis
```

- 只有 web 对外开放端口，数据库和 Redis 不映射端口，公网无法直接访问
- 前端、后端都在 Docker 里编译（多阶段构建），服务器上不需要安装 JDK、Maven、Node
- 密码和 API Key 放在 `.env`，不提交到 Git，也不会打进镜像（见 `.dockerignore`）

## 一、本机先跑一遍（推荐）

在自己电脑上用 Docker Desktop 跑通生产环境，再去买服务器。

```powershell
cd D:\code\safework
Copy-Item .env.example .env
notepad .env        # 修改两个密码；WEB_PORT 改成 8088（本机 80 端口可能被占用）
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

浏览器打开 http://localhost:8088

## 二、服务器选择

| | 香港 / 海外服务器 | 国内服务器 |
|---|---|---|
| 备案 | **不需要** | 绑定域名必须先 ICP 备案（1～3 周） |
| 拉取 Docker 镜像、访问 GitHub | 正常 | 经常失败，需要配置镜像加速 |
| 价格（2 核 2G 轻量） | 约 30～40 元 / 月 | 学生优惠可低至几元 / 月 |

配置要求：**2 核 2G 起步，推荐 2 核 4G**，系统选 **Ubuntu 22.04**。

## 三、服务器部署

1. 在云服务器控制台的**防火墙 / 安全组**中放行 **22**（SSH）和 **80**（HTTP）端口
2. 用 PowerShell 登录服务器：`ssh root@服务器IP`
3. 上传代码（二选一）：
   - 服务器能访问 GitHub：`git clone https://github.com/你的用户名/safework.git /opt/safework`
   - 不能访问：在本机打包后上传
     ```powershell
     cd D:\code
     tar -czf safework.tar.gz --exclude=node_modules --exclude=target --exclude=.git --exclude=.env --exclude=secret.yml safework
     scp safework.tar.gz root@服务器IP:/opt/
     ```
     服务器上：`cd /opt && tar -xzf safework.tar.gz`
4. 初始化服务器并启动：
   ```bash
   cd /opt/safework
   sudo bash deploy/server-setup.sh
   cp .env.example .env
   nano .env            # 修改密码，填 AI_CHAT_API_KEY；WEB_PORT 保持 80
   bash deploy/update.sh
   ```
5. 浏览器打开 `http://服务器IP`

## 四、日常运维

| 操作 | 命令 |
|---|---|
| 发布新版本 | 上传新代码后 `bash deploy/update.sh` |
| 查看状态 | `docker compose -f docker-compose.prod.yml ps` |
| 看后端日志 | `docker compose -f docker-compose.prod.yml logs -f --tail=200 backend` |
| 备份数据库 | `bash deploy/backup.sh` |
| 重启某个服务 | `docker compose -f docker-compose.prod.yml restart backend` |

## 五、注意

- `sql/init.sql` 只在**第一次**创建数据库时执行。之后修改表结构，执行 `sql/upgrade/` 里对应版本的升级脚本，不能删数据卷重建（会丢数据）：
  ```bash
  docker compose -f docker-compose.prod.yml exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 safework' < sql/upgrade/v2-platform-security.sql
  # 之后的版本同理，例如 v3-message-job.sql，按版本号顺序执行
  ```
- 平台管理员账号 `platform / admin / admin123` 部署后**第一时间修改密码**（右上角 → 修改密码）
- 生产环境 `SPRING_PROFILES_ACTIVE=prod`：验证码答案不会返回给前端，测试脚本不能直接对生产环境运行
- 演示环境的账号是公开的，任何人都能调用 AI 接口：DeepSeek 账户**只充少量余额**，后端已按用户限流
- Nginx 已关闭 `/api` 的缓冲（`proxy_buffering off`），否则 AI 回答不会逐字显示
