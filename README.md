# SafeWork 智能安全作业管理平台

面向工贸、化工企业的多租户 SaaS：受限空间、动火、高处等特殊作业的作业票申请、审批与台账管理，并内置 AI 安全规程问答与作业票智能预审。

## 当前进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 项目骨架、多租户隔离、登录认证 | ✅ 本版本 |
| 1 | 角色、菜单、按钮级权限（RBAC） | 下一步 |
| 2 | 作业票、审批流、文件上传、前端页面 | 待开始 |
| 3 | AI 知识库问答（RAG）、作业票智能预审 | 待开始 |
| 4 | 服务器部署、HTTPS、CI/CD | 待开始 |

## 技术栈

Spring Boot 3.3 · MyBatis-Plus 3.5 · Sa-Token · MySQL 8 · Redis 7 · Docker Compose

## 项目结构

```
safework
├── safework-common   公共模块：统一返回 R、业务异常、全局异常处理、租户上下文、实体基类
├── safework-system   系统模块：租户、用户、登录（后续加入角色、菜单）
├── safework-server   启动模块：配置（租户插件、登录拦截）并打包成可运行 jar
├── sql/init.sql      建表脚本和演示数据
├── docker-compose.yml  本地启动 MySQL + Redis
└── test-api.ps1      接口自测脚本
```

采用**模块化单体**：业务按 Maven 模块划分、边界清晰，但只部署一个进程，适合 2 核 4G 的个人服务器。后续需要时可以把 AI 模块单独拆成服务。

## 多租户设计

1. 用户登录时填写**企业编码**，后端校验企业状态和有效期后，在该企业范围内校验账号密码。
2. 登录成功后，租户 ID 存入 Sa-Token 会话（Redis）。
3. 每个请求由拦截器从会话中取出租户 ID，放入 `TenantContext`（ThreadLocal），请求结束后清理，防止线程复用导致串租户。
4. MyBatis-Plus `TenantLineInnerInterceptor` 自动给 SQL 拼接 `tenant_id = ?`，业务代码不需要手写租户条件；新增数据时自动写入 `tenant_id`。
5. **失败即拒绝**：拿不到租户 ID 时直接抛异常，而不是放行成查询全部租户的数据。
6. `sys_tenant` 等平台级表通过 `ignoreTable` 排除在隔离之外。

## 演示账号

| 企业编码 | 用户名 | 密码 | 说明 |
|---|---|---|---|
| demo | admin | admin123 | 演示化工，3 个用户 |
| test | admin | admin123 | 测试冶金，2 个用户 |

## 接口

| 方法 | 路径 | 说明 | 需登录 |
|---|---|---|---|
| POST | /auth/login | 登录，返回 token | 否 |
| GET | /auth/me | 当前登录用户 | 是 |
| POST | /auth/logout | 退出登录 | 是 |
| GET | /system/user/page | 用户分页（current, size, keyword） | 是 |
| POST | /system/user | 新增用户 | 是 |
| GET | /actuator/health | 健康检查 | 否 |

请求头：`Authorization: Bearer <token>`

## 本地运行

```powershell
docker compose up -d
mvn clean package -DskipTests
java -jar safework-server\target\safework-server.jar
powershell -ExecutionPolicy Bypass -File .\test-api.ps1
```
