# SafeWork 智能安全作业管理平台

面向工贸、化工企业的多租户 SaaS：受限空间、动火、高处等特殊作业的作业票申请、审批与台账管理，并内置 AI 安全规程问答与作业票智能预审。

## 当前进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 项目骨架、多租户隔离、登录认证 | ✅ 完成 |
| 1 | 角色、菜单、按钮级权限（RBAC） | ✅ 完成 |
| 2 | 作业票、审批流（状态机、乐观锁、数据权限） | ✅ 完成 |
| 2 | 文件上传、前端页面 | 下一步 |
| 3 | AI 知识库问答（RAG）、作业票智能预审 | 待开始 |
| 4 | 服务器部署、HTTPS、CI/CD | 待开始 |

## 技术栈

Spring Boot 3.3 · MyBatis-Plus 3.5 · Sa-Token · MySQL 8 · Redis 7 · Docker Compose

## 项目结构

```
safework
├── safework-common   公共模块：统一返回 R、业务异常、全局异常处理、租户上下文、实体基类
├── safework-system   系统模块：租户、用户、登录、角色、菜单权限
├── safework-ticket   作业票模块：作业票、审批流状态机、操作记录
├── safework-server   启动模块：配置（租户插件、登录拦截）并打包成可运行 jar
├── sql/init.sql      建表脚本和演示数据
├── docker-compose.yml  本地启动 MySQL + Redis
├── test-api.ps1      登录、租户、权限自测脚本
└── test-ticket.ps1   作业票审批流自测脚本
```

采用**模块化单体**：业务按 Maven 模块划分、边界清晰，但只部署一个进程，适合 2 核 4G 的个人服务器。后续需要时可以把 AI 模块单独拆成服务。

## 多租户设计

1. 用户登录时填写**企业编码**，后端校验企业状态和有效期后，在该企业范围内校验账号密码。
2. 登录成功后，租户 ID 存入 Sa-Token 会话（Redis）。
3. 每个请求由拦截器从会话中取出租户 ID，放入 `TenantContext`（ThreadLocal），请求结束后清理，防止线程复用导致串租户。
4. MyBatis-Plus `TenantLineInnerInterceptor` 自动给 SQL 拼接 `tenant_id = ?`，业务代码不需要手写租户条件；新增数据时自动写入 `tenant_id`。
5. **失败即拒绝**：拿不到租户 ID 时直接抛异常，而不是放行成查询全部租户的数据。
6. `sys_tenant` 等平台级表通过 `ignoreTable` 排除在隔离之外。

## RBAC 权限设计

- **5 张表**：`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`
- **菜单是平台级**（所有企业共用一套功能菜单），**角色和关联表按企业隔离**（每个企业自己定义角色、分配权限）
- 菜单分三种：`M` 目录、`C` 页面、`F` 按钮；按钮上的 `perms` 就是接口权限码
- 接口用 `@SaCheckPermission("system:user:add")` 声明所需权限，Sa-Token 通过 `StpInterfaceImpl` 查询当前用户权限
- 企业管理员（`role_code = admin`）直接返回 `*` 通配符，不需要逐个分配
- `/auth/me` 一次返回用户信息、角色、权限码、菜单树，前端据此生成动态路由和控制按钮显示
- **越权防护**：分配角色时，用户和角色都通过租户插件查询，别的企业的 ID 查不到，直接返回“不存在”
- **拦截器顺序**：租户拦截器必须排在 Sa-Token 拦截器前面，因为权限注解校验时要查库，需要先有租户上下文

| 角色 | 编码 | 权限 |
|---|---|---|
| 企业管理员 | admin | 全部 |
| 安全员 | safety | 查看、审批作业票 |
| 作业负责人 | leader | 查看、申请作业票 |
| 监护人 | guardian | 查看作业票 |

## 作业票审批流设计

```
 DRAFT ──提交──▶ PENDING ──批准──▶ APPROVED ──开工──▶ IN_PROGRESS ──完工──▶ COMPLETED
   ▲               │
   │              驳回
   │               ▼
   └──修改后提交── REJECTED        （草稿 / 待审批 / 已驳回 / 已批准 可以取消 → CANCELLED）
```

- **状态机**：`TicketAction` 枚举集中定义每个动作的起始状态和目标状态，所有流转都经过 `TicketService.fire()` 这一个入口，不允许直接改状态，杜绝跳步
- **两层权限**：接口注解控制功能权限（有没有审批权限）；Service 校验业务身份（是不是这张票的申请人 / 指定监护人）
- **职责分离**：申请人不能审批自己的票，管理员也不例外；监护人不能由申请人兼任
- **业务校验**：受限空间、动火作业提交时必须有合格的气体检测数据；开工前检测结果必须在 30 分钟内，监护人可现场复测录入（阈值为简化规则，真实项目按 GB 30871-2022 细化并做成配置）
- **乐观锁**：`@Version` 字段，两个安全员同时审批同一张票，只有一个成功，另一个返回“已被他人处理”
- **数据权限**：有审批权限的人看全部；其他人只能看到自己申请或自己监护的票；越权查看返回 404，不暴露数据是否存在
- **操作留痕**：每次流转写入 `work_ticket_log`，与状态更新在同一事务中
- **编号生成**：Redis `INCR` 按 企业 + 类型 + 日期 生成流水号，如 `SX20261028-0001`
- **前端友好**：详情接口返回当前用户可执行的 `actions`，前端直接据此显示按钮

## 演示账号

| 企业编码 | 用户名 | 密码 | 说明 |
|---|---|---|---|
| demo | admin | admin123 | 演示化工 · 企业管理员 |
| demo | zhangsan | admin123 | 演示化工 · 安全员 |
| demo | lisi | admin123 | 演示化工 · 作业负责人 |
| demo | zhaoliu | admin123 | 演示化工 · 监护人 |
| test | admin | admin123 | 测试冶金 · 企业管理员 |
| test | wangwu | admin123 | 测试冶金 · 监护人 |

## 接口

| 方法 | 路径 | 说明 | 所需权限 |
|---|---|---|---|
| POST | /auth/login | 登录，返回 token | 否 |
| GET | /auth/me | 当前用户 + 角色 + 权限码 + 菜单树 | 是 |
| POST | /auth/logout | 退出登录 | 是 |
| GET | /system/user/page | 用户分页（current, size, keyword） | system:user:list |
| POST | /system/user | 新增用户 | system:user:add |
| GET/PUT | /system/user/{id}/roles | 查询 / 分配用户角色 | system:user:role |
| GET | /system/role/list | 角色列表 | system:role:list |
| POST | /system/role | 新增角色 | system:role:add |
| GET/PUT | /system/role/{id}/menus | 查询 / 分配角色菜单 | system:role:menu |
| GET | /system/menu/tree | 全部菜单树 | system:menu:list |
| GET | /ticket/page | 作业票分页（status, ticketType, keyword） | ticket:list |
| GET | /ticket/{id} | 详情 + 操作记录 + 可执行动作 | ticket:list |
| POST / PUT | /ticket、/ticket/{id} | 新建 / 修改（草稿、已驳回） | ticket:add |
| POST | /ticket/{id}/submit、cancel | 提交审批 / 取消 | ticket:add + 申请人 |
| POST | /ticket/{id}/approve、reject | 批准 / 驳回 | ticket:approve + 非申请人 |
| POST | /ticket/{id}/start、complete | 开工 / 完工 | 指定监护人 |
| GET | /actuator/health | 健康检查 | 否 |

请求头：`Authorization: Bearer <token>`；时间格式：`yyyy-MM-dd HH:mm:ss`

## 本地运行

```powershell
docker compose up -d
mvn clean package -DskipTests
java -jar safework-server\target\safework-server.jar
powershell -ExecutionPolicy Bypass -File .\test-api.ps1
powershell -ExecutionPolicy Bypass -File .\test-ticket.ps1
```
