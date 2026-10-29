# SafeWork 智能安全作业管理平台

面向工贸、化工企业的多租户 SaaS：受限空间、动火、高处等特殊作业的作业票申请、审批与台账管理，并内置 AI 安全规程问答与作业票智能预审。

## 当前进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 项目骨架、多租户隔离、登录认证 | ✅ 完成 |
| 1 | 角色、菜单、按钮级权限（RBAC） | ✅ 完成 |
| 2 | 作业票、审批流（状态机、乐观锁、数据权限） | ✅ 完成 |
| 2 | 前端（Vue3 + Element Plus，动态路由、按钮权限） | ✅ 完成 |
| 2 | 受限空间台账 + 现场二维码、隐患排查闭环 | ✅ 完成 |
| 2 | 部门、用户管理完善、操作日志（AOP） | ✅ 完成 |
| 2 | 文件上传（现场照片、附件）、数据看板 | 下一步 |
| 3 | AI 知识库问答（RAG）、作业票智能预审 | 待开始 |
| 4 | 服务器部署、HTTPS、CI/CD | 待开始 |

## 技术栈

**后端**：Spring Boot 3.3 · MyBatis-Plus 3.5 · Sa-Token · MySQL 8 · Redis 7

**前端**：Vue 3.5 · Vite 5 · Element Plus · Pinia · Vue Router · Axios

**部署**：Docker Compose

## 项目结构

```
safework
├── safework-common   公共模块：统一返回 R、业务异常、全局异常处理、租户上下文、实体基类
├── safework-system   系统模块：租户、部门、用户、登录、角色、菜单权限、操作日志切面
├── safework-safety   安全管理模块：受限空间台账、隐患排查治理
├── safework-ticket   作业票模块：作业票、审批流状态机、操作记录
├── safework-server   启动模块：配置（租户插件、登录拦截）并打包成可运行 jar
├── safework-web      前端：Vue3 + Element Plus
├── sql/init.sql      建表脚本和演示数据
├── docker-compose.yml  本地启动 MySQL + Redis
├── test-api.ps1      登录、租户、权限自测脚本
├── test-ticket.ps1   作业票审批流自测脚本
├── test-safety.ps1   受限空间、扫码、隐患闭环自测脚本
└── test-system.ps1   部门、用户管理、操作日志自测脚本
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

## 受限空间台账与现场二维码

- 每个受限空间登记编号、类型、位置、所属部门、责任人、危险介质、风险等级
- **受限空间作业票必须关联台账中的空间**；空间停用后不能再申请作业
- **同一空间时间冲突校验**：计划时间重叠的有效作业票（已批准、作业中）只能有一张，避免两个班组同时进入
- **现场二维码**：贴在空间入口，任何人扫码（无需登录）都能看到危险介质、当前是否有人作业、作业票和气体检测结果；没有有效作业票时显示“禁止进入”
  - 难点：匿名访问没有租户上下文。做法是用 `@InterceptorIgnore(tenantLine = "true")` 只让“按令牌查空间”这一个查询跳过租户插件，再把空间所属租户放进 `TenantContext`，后续查询照常隔离
  - 安全：二维码里是 32 位随机令牌而不是自增 ID，无法遍历；列表接口不返回令牌；可以一键重新生成让旧二维码失效

## 隐患排查治理（闭环）

```
 上报 → REPORTED ──指派──▶ RECTIFYING ──提交整改──▶ PENDING_VERIFY ──验收通过──▶ CLOSED
          │                    ▲                          │
         撤销                   └────────验收不通过─────────┘
          ▼
       REVOKED
```

- 和作业票同样的状态机 + 乐观锁 + 处理记录写法，验证这套设计可以复用
- 任何人都可以上报；安全员指派整改人和期限；**只有整改人本人**能提交整改；**整改人不能验收自己的整改**
- 逾期提醒：整改中且超过期限的隐患标红，可以单独筛选，首页显示逾期数量

## 系统管理

- **部门**：树形结构；按部门筛选用户时包含所有下级部门；禁止把部门移到自己的下级下面（防止成环）；有下级或有用户时不能删除
- **用户**：修改、停用（**立即踢下线**，`StpUtil.logout(userId)`）、重置密码（强制重新登录）、修改自己的密码；列表批量填充部门和角色名称，避免 N+1 查询
- **操作日志**：自定义注解 `@OperLog(module, action)` + AOP 切面自动记录操作人、IP、参数、结果、耗时；密码字段自动脱敏；失败的操作也会记录原因；记录日志出错不影响业务

## 前端设计

- **动态路由**：登录后调用 `/auth/me`，根据后端返回菜单的 `component` 字段，通过 `import.meta.glob` 找到页面组件并 `router.addRoute` 注册；没有权限的页面根本不会注册，直接访问显示 404
- **按钮权限**：自定义指令 `v-perm="'ticket:add'"`，没有权限时移除按钮（只是体验优化，真正的校验在后端）
- **作业票按钮由后端决定**：详情接口返回当前用户可执行的 `actions`，前端不重复写“谁能做什么”的规则，避免前后端规则不一致
- **统一请求封装**：Axios 拦截器自动带 token，统一处理 `{code, msg, data}`，401 自动跳回登录页
- **开发代理**：Vite 把 `/api/*` 转发到 `localhost:8080` 并去掉前缀，部署时由 Nginx 做同样的事

## 演示账号

| 企业编码 | 用户名 | 密码 | 说明 |
|---|---|---|---|
| demo | admin | admin123 | 演示化工 · 企业管理员 |
| demo | zhangsan | admin123 | 演示化工 · 安全员 |
| demo | lisi | admin123 | 演示化工 · 作业负责人 |
| demo | zhaoliu | admin123 | 演示化工 · 监护人 |

扫码演示：登录管理员 → 安全管理 → 受限空间 → 二维码 → 在新窗口打开扫码页
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
| GET | /ticket/guardians | 本企业监护人列表（新建时下拉选择） | ticket:add |
| POST / PUT | /ticket、/ticket/{id} | 新建 / 修改（草稿、已驳回） | ticket:add |
| POST | /ticket/{id}/submit、cancel | 提交审批 / 取消 | ticket:add + 申请人 |
| POST | /ticket/{id}/approve、reject | 批准 / 驳回 | ticket:approve + 非申请人 |
| POST | /ticket/{id}/start、complete | 开工 / 完工 | 指定监护人 |
| GET / POST / PUT / DELETE | /system/dept/tree、/system/dept | 部门树 / 增 / 改 / 删 | system:dept:* |
| PUT | /system/user/{id}、/{id}/status、/{id}/password | 修改 / 启停 / 重置密码 | system:user:edit、reset |
| GET | /system/user/options | 人员下拉 | 登录即可 |
| POST | /auth/password | 修改自己的密码 | 登录即可 |
| GET | /system/log/page | 操作日志 | system:log:list |
| GET / POST / PUT | /safety/space/page、/safety/space | 受限空间台账 | safety:space:* |
| GET | /safety/space/{id}/qr-token | 获取二维码令牌 | safety:space:edit |
| GET | /public/space/{token} | 扫码查看（匿名） | 否 |
| GET / POST | /safety/hazard/page、/safety/hazard | 隐患列表 / 上报 | safety:hazard:list、report |
| POST | /safety/hazard/{id}/assign、revoke、verify-pass、verify-fail | 指派 / 撤销 / 验收 | safety:hazard:handle |
| POST | /safety/hazard/{id}/rectify | 提交整改 | 指定整改人 |
| GET | /actuator/health | 健康检查 | 否 |

请求头：`Authorization: Bearer <token>`；时间格式：`yyyy-MM-dd HH:mm:ss`

## 本地运行

```powershell
# 1. 数据库和 Redis
docker compose up -d

# 2. 后端（窗口 1）
mvn clean package -DskipTests
java -jar safework-server\target\safework-server.jar

# 3. 前端（窗口 2），浏览器打开 http://localhost:5173
cd safework-web
npm install
npm run dev

# 4. 接口自测（窗口 3）
powershell -ExecutionPolicy Bypass -File .\test-api.ps1
powershell -ExecutionPolicy Bypass -File .\test-ticket.ps1
powershell -ExecutionPolicy Bypass -File .\test-safety.ps1
powershell -ExecutionPolicy Bypass -File .\test-system.ps1
```
