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

## 三（推荐）、2G 内存服务器：本机编译、上传发布包

服务器内存只有 2G 时，在服务器上用 Docker 编译 Java 和前端很容易内存不足。改成在自己电脑上编译，服务器只负责运行：

```
本机：deploy\pack.ps1 ──▶ 编译 jar + 前端 dist ──▶ safework-release.tar.gz（几十 MB）
                                                  │ scp 上传
服务器：/opt/safework ──▶ bash start.sh ──▶ 只把 jar、dist 放进 JRE / Nginx 镜像，不编译
```

```powershell
# 本机（项目根目录）
powershell -ExecutionPolicy Bypass -File .\deploy\pack.ps1
scp D:\code\safework-release.tar.gz root@服务器IP:/root/
```

```bash
# 服务器
mkdir -p /opt/safework && tar -xzf /root/safework-release.tar.gz -C /opt/safework && cd /opt/safework
bash server-init.sh https://你的专属地址.mirror.aliyuncs.com   # 只需一次：交换空间、Docker、镜像加速
cp .env.example .env && nano .env                              # 只需一次：修改密码、填 API Key
bash start.sh
```

- 国内服务器连不上 Docker Hub：`server-init.sh` 会配置镜像加速。阿里云服务器到「容器镜像服务 ACR → 镜像工具 → 镜像加速器」复制专属地址传给脚本，另外还配置了两个公共加速地址备用
- 发布新版本：本机重新运行 `pack.ps1`、上传，服务器解压到 `/opt/safework`（会覆盖程序文件，不会动 `.env` 和数据卷）后再 `bash start.sh`
- 数据卷名称和 `docker-compose.prod.yml` 一样（项目名都是 `safework-prod`），以后换成在服务器上编译也不会丢数据
- 备份：`bash backup.sh`

### 打开演示模式（把地址公开给别人自测）

```bash
cd /opt/safework
grep -q '^DEMO_MODE=' .env && sed -i 's/^DEMO_MODE=.*/DEMO_MODE=true/' .env || echo 'DEMO_MODE=true' >> .env   # 打开演示模式
bash start.sh                                     # 重启后生效
# 先用 platform / admin 登录改好平台管理员密码、准备好演示数据，再保存基线：
bash demo-baseline.sh
# 每天 04:00 自动恢复
(crontab -l 2>/dev/null; echo "0 4 * * * cd /opt/safework && bash demo-reset.sh >> demo/reset.log 2>&1") | crontab -
```

- 恢复会把整个数据库还原成基线：之后再改平台管理员密码、加演示数据，要重新运行 `demo-baseline.sh`
- 发布新版本不用重新保存基线：恢复后后端启动时 Flyway 会自动执行新增的数据库版本
- 手动恢复一次：`bash demo-reset.sh`（停机约 1 分钟，所有人需要重新登录）

## 四、日常运维

| 操作 | 命令 |
|---|---|
| 发布新版本 | 上传新代码后 `bash deploy/update.sh` |
| 查看状态 | `docker compose -f docker-compose.prod.yml ps` |
| 看后端日志 | `docker compose -f docker-compose.prod.yml logs -f --tail=200 backend` |
| 备份数据库 | `bash deploy/backup.sh` |
| 重启某个服务 | `docker compose -f docker-compose.prod.yml restart backend` |

## 五、注意

- 数据库表结构由 **Flyway** 管理：后端启动时自动执行 `db/migration` 里新增的版本脚本，发布新版本只需要 `bash deploy/update.sh`，**不能删数据卷重建**（会丢数据）。执行记录可以查 `flyway_schema_history` 表
- 后端日志同时写到数据卷 `backend-logs`（`/app/logs/safework.log`，单文件 20MB 滚动、保留 14 天）。用户报错误编号时：`docker compose -f docker-compose.prod.yml exec backend grep 错误编号 /app/logs/safework.log`
- 平台管理员账号 `platform / admin / admin123` 部署后**第一时间修改密码**（右上角 → 修改密码）
- 生产环境 `SPRING_PROFILES_ACTIVE=prod`：验证码答案不会返回给前端，测试脚本不能直接对生产环境运行
- 演示环境的账号是公开的，任何人都能调用 AI 接口：DeepSeek 账户**只充少量余额**，后端已按用户限流
- Nginx 已关闭 `/api` 的缓冲（`proxy_buffering off`），否则 AI 回答不会逐字显示
