# SafeWork 智能安全作业管理平台

[![CI](https://github.com/qianjingguo666-lang/safework/actions/workflows/ci.yml/badge.svg)](https://github.com/qianjingguo666-lang/safework/actions/workflows/ci.yml)

面向工贸、化工企业的多租户 SaaS：受限空间、动火、高处等特殊作业的作业票申请、审批与台账管理，并内置 AI 安全规程问答与作业票智能预审。平台端负责企业入驻、套餐、续费和停用。

## 当前进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 项目骨架、多租户隔离、登录认证 | ✅ 完成 |
| 1 | 角色、菜单、按钮级权限（RBAC） | ✅ 完成 |
| 2 | 作业票、审批流（状态机、乐观锁、数据权限） | ✅ 完成 |
| 2 | 前端（Vue3 + Element Plus，动态路由、按钮权限） | ✅ 完成 |
| 2 | 受限空间台账 + 现场二维码、隐患排查闭环 | ✅ 完成 |
| 2 | 部门、用户管理完善、操作日志（AOP） | ✅ 完成 |
| 2 | SaaS 平台端：企业开通、套餐、到期续费、停用 | ✅ 完成 |
| 2 | 登录安全：图形验证码、失败锁定、登录日志、在线用户强制下线 | ✅ 完成 |
| 2 | 消息通知（实时推送、待办提醒、通知公告）+ 定时任务（分布式锁、执行记录） | ✅ 完成 |
| 2 | 附件（隐患现场 / 整改照片、作业票附件）、Excel 导入导出、数据看板（ECharts） | ✅ 完成 |
| 3 | AI 知识库问答（RAG，流式输出、引用出处）、作业票 AI 预审 | ✅ 完成 |
| 4 | Docker 部署（多阶段构建、Nginx 反向代理、一键发布、数据库备份） | ✅ 完成 |
| 4 | 工程规范：Flyway 数据库版本管理、接口文档、单元测试、traceId 链路日志、GitHub Actions CI | ✅ 完成 |
| 4 | 域名 + HTTPS | 下一步 |

## 技术栈

**后端**：Spring Boot 3.3 · MyBatis-Plus 3.5 · Sa-Token · MySQL 8 · Redis 7

**前端**：Vue 3.5 · Vite 5 · Element Plus · Pinia · Vue Router · Axios · ECharts 5（按需引入）

**其他**：EasyExcel（导入导出）· Flyway（数据库版本管理）· springdoc-openapi（接口文档）· JUnit 5 + Mockito · GitHub Actions

**AI**：讯飞星火（OpenAI 兼容接口）· 阿里云百炼向量模型 · PDFBox · SSE 流式输出

**部署**：Docker 多阶段构建 · Docker Compose · Nginx（静态资源 + 反向代理 + SSE）

## 项目结构

```
safework
├── safework-common   公共模块：统一返回 R、业务异常、全局异常处理、租户上下文、实体基类
├── safework-system   系统模块：平台端（企业、套餐）、部门、用户、登录安全、角色、菜单权限、日志
├── safework-safety   安全管理模块：受限空间台账、隐患排查治理
├── safework-ticket   作业票模块：作业票、审批流状态机、操作记录
├── safework-ai       AI 模块：知识库（解析、切分、向量）、混合检索、流式问答、作业票预审
├── safework-server   启动模块：配置（租户插件、登录拦截）并打包成可运行 jar
├── safework-web      前端：Vue3 + Element Plus
├── safework-server/src/main/resources/db/migration/  Flyway 数据库脚本（V1 建表 + 演示数据，之后每次改表加一个新版本）
├── .github/workflows/ci.yml  GitHub Actions：自动编译、跑单元测试、构建前端
├── config/secret.example.yml  AI 密钥配置模板（复制为 secret.yml，不会提交）
├── docs/knowledge/   示例制度文件《特殊作业安全管理制度（示例）》，可上传到知识库测试
├── docker-compose.yml       本地开发：只启动 MySQL + Redis
├── docker-compose.prod.yml  生产部署：MySQL + Redis + 后端 + 前端（Nginx）
├── Dockerfile               后端镜像（多阶段构建）
├── deploy/                  服务器初始化、发布、备份脚本
├── docs/deploy.md           部署说明
├── test-api.ps1      登录、租户、权限自测脚本
├── test-ticket.ps1   作业票审批流自测脚本
├── test-safety.ps1   受限空间、扫码、隐患闭环自测脚本
├── test-system.ps1   部门、用户管理、操作日志自测脚本
├── test-ai.ps1       AI 知识库问答、预审、限流自测脚本
├── test-platform.ps1 平台端（开通企业、套餐）+ 登录安全自测脚本
├── test-message.ps1  消息推送、业务通知、公告、定时任务自测脚本
└── test-file.ps1     附件权限、Excel 导入导出、数据看板自测脚本
```

采用**模块化单体**：业务按 Maven 模块划分、边界清晰，但只部署一个进程，适合 2 核 4G 的个人服务器。后续需要时可以把 AI 模块单独拆成服务。

## 多租户设计

1. 用户登录时填写**企业编码**，后端校验企业状态和有效期后，在该企业范围内校验账号密码。
2. 登录成功后，租户 ID 存入 Sa-Token 会话（Redis）。
3. 每个请求由拦截器从会话中取出租户 ID，放入 `TenantContext`（ThreadLocal），请求结束后清理，防止线程复用导致串租户。
4. MyBatis-Plus `TenantLineInnerInterceptor` 自动给 SQL 拼接 `tenant_id = ?`，业务代码不需要手写租户条件；新增数据时自动写入 `tenant_id`。
5. **失败即拒绝**：拿不到租户 ID 时直接抛异常，而不是放行成查询全部租户的数据。
6. `sys_tenant`、`sys_tenant_package`、`sys_menu` 等平台级表通过 `ignoreTable` 排除在隔离之外。
7. **停用 / 到期立即生效**：租户拦截器每个请求都检查企业状态（30 秒本地缓存，平台修改时主动清除），已登录的用户下一次请求就被拒绝，不用等 token 过期。
8. **平台操作别的企业**：开通企业时用 `TenantContext.runAs(新企业ID, ...)` 临时切换租户写入初始数据，而不是关掉租户插件 —— 即使代码写错，影响范围也只有被切换到的那个企业。

## RBAC 权限设计

- **5 张表**：`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`
- **菜单是平台级**（所有企业共用一套功能菜单），**角色和关联表按企业隔离**（每个企业自己定义角色、分配权限）
- 菜单分三种：`M` 目录、`C` 页面、`F` 按钮；按钮上的 `perms` 就是接口权限码
- 接口用 `@SaCheckPermission("system:user:add")` 声明所需权限，Sa-Token 通过 `StpInterfaceImpl` 查询当前用户权限
- **最终权限 = 角色分配的菜单 ∩ 企业套餐包含的菜单**。企业管理员（`role_code = admin`）拥有套餐内全部菜单，不需要逐个分配；没有用 `*` 通配符，否则套餐限制不住管理员
- 套餐降级（旗舰版 → 标准版）后，角色里多出来的菜单自动失效，不需要逐个修改角色；给角色分配套餐外的菜单会被拒绝
- 菜单有作用范围 `scope`：`T` 企业端（可以放进套餐）、`P` 平台端（企业管理、套餐管理，只有运营平台能看到）
- `/auth/me` 一次返回用户信息、角色、权限码、菜单树，前端据此生成动态路由和控制按钮显示
- **越权防护**：分配角色时，用户和角色都通过租户插件查询，别的企业的 ID 查不到，直接返回“不存在”
- **拦截器顺序**：租户拦截器必须排在 Sa-Token 拦截器前面，因为权限注解校验时要查库，需要先有租户上下文

| 角色 | 编码 | 权限 |
|---|---|---|
| 企业管理员 | admin | 套餐内全部 |
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

## SaaS 平台端

运营平台本身也是一个租户（`tenant_id = 0`，企业编码 `platform`），平台管理员和企业用户用同一套登录、权限代码，只是菜单不同。

- **开通企业**：一个事务里完成：新建企业 → 切换到新企业创建根部门、企业管理员、安全员 / 作业负责人 / 监护人预置角色（按套餐分配菜单）→ 创建管理员账号。任何一步失败全部回滚
- **套餐**：旗舰版（全部功能）、标准版（不含 AI）、基础版（只有作业票），平台可以自定义；修改套餐后使用它的企业立即按新范围生效；平台菜单不能放进套餐；有企业在用的套餐不能停用 / 删除
- **服务期限、账号上限**：到期前 30 天在企业页面顶部提醒续费；到期后已登录用户立即失效、不能再登录；新增用户超过上限时拒绝
- **停用企业**：该企业所有在线用户立即被踢下线
- **平台首页**：企业数、即将到期企业、账号数、在线人数、今日登录成功 / 失败次数
- **双重防线**：`/platform/**` 接口既要求平台权限码，租户拦截器还会检查当前租户必须是平台，企业管理员绕不过去

## 登录安全

- **图形验证码**：算术题（如 `7+5=?`），答案存在 Redis，2 分钟过期，**校验一次就作废**（不管对错），防止拿同一个验证码反复试密码。图片用 SVG 折线笔画绘制：不依赖服务器字体（Docker 精简镜像里 `java.awt` 画字经常出问题），也没有把文字明文写进 `<text>` 标签
- **校验顺序**：验证码 → 企业 → 账号锁定 → 密码 → 账号状态。没有正确验证码的请求连“企业是否存在、账号是否锁定”都探测不到
- **失败锁定**：同一企业同一用户名 15 分钟内连续输错 5 次，锁定 15 分钟（可配置）。用户名不存在也计数（防止通过“锁没锁”枚举账号）；用户名转小写计数（MySQL 不区分大小写，防止 `Admin` / `admin` 分开计数被绕过）；企业管理员可以在用户列表一键解锁
- **登录日志**：成功、失败都记录 IP、浏览器、操作系统、失败原因；企业看自己的，平台能看全部（排查“我登不上去”、发现撞库）
- **在线用户**：每个企业一个 Redis Hash 记录会话；接口只返回 token 的 SHA-256 摘要作为会话 ID，**不返回 token 本身**；强制下线后对方下一次请求收到“您已被管理员强制下线”；不能踢自己当前的会话；退出、停用、过期的会话在查询时懒删除
- **开发 / 生产配置分离**：开发环境（`application-dev.yml`）获取验证码时会把答案一起返回，测试脚本靠它自动登录；生产环境（`SPRING_PROFILES_ACTIVE=prod`）不返回

## 消息通知

- **谁会收到什么**：作业票提交 → 所有有审批权限的人收到待办；批准 → 申请人收到通知、监护人收到“待开工”；驳回 → 申请人收到待办（含原因）。隐患上报 → 安全员（重大隐患是“预警”）；指派 → 整改人；整改完成 → 验收人（不含整改人自己）；闭环 → 上报人。操作人自己不会收到
- **在状态机里统一发送**：通知写在 `fire()`（状态流转唯一入口）里，不会出现“某个按钮忘了发通知”
- **和业务同一个事务**：审批失败回滚，“已批准”的消息也不存在；**实时推送放在事务提交之后**（`TransactionSynchronization.afterCommit`），避免用户点开消息看到的还是旧状态
- **实时推送**：浏览器和后端保持一个 SSE 长连接，新消息右下角弹窗、铃铛红点 +1。选择 SSE 而不是 WebSocket：只需要服务器 → 浏览器单向推送，SSE 就是普通 HTTP，登录校验和 Nginx 配置都和其他接口一样，断线自动重连
- **多台服务器**：用户的长连接可能在 A 机器，发消息的请求在 B 机器。发送时发布到 **Redis 频道**，每台机器都订阅，只推给连在自己身上的用户
- **长连接维护**：每 25 秒心跳（防止 Nginx 60 秒断开空闲连接）；30 分钟主动结束让前端重连（清理僵尸连接）；每人最多 5 个连接；被强制下线、被停用时立即断开
- **通知公告**：企业管理员发布，全员各生成一条消息；可以撤回（消息一起删除）
- **越权防护**：标记已读的条件里带 `user_id`，加上租户插件，别人的、别的企业的消息都改不了

## 定时任务

| 任务 | 时间 | 做什么 |
|---|---|---|
| 隐患整改期限检查 | 每天 8:30 | 已逾期：每天提醒整改人和安全员；24 小时内到期：提醒整改人 |
| 作业票超时检查 | 每 30 分钟 | 作业中但超过计划结束时间（人可能还在受限空间里）、已批准但过期未开工 |
| 企业到期提醒 | 每天 9:00 | 到期前 30 / 7 / 3 / 1 天提醒企业管理员，并给平台发汇总 |
| 过期数据清理 | 每天 3:00 | 删除过期的登录日志、已读消息、任务执行记录 |

- **框架**：实现 `ScheduledJob` 接口就自动注册调度、出现在平台的【定时任务】页面（可以手动执行、看执行记录）
- **定时任务没有租户上下文**：没有 Web 请求，`TenantContext` 是空的，直接查业务表会被租户插件拒绝。所以按企业逐个 `runAs` 执行，某个企业出错不影响其他企业
- **分布式锁**：多台服务器同时到点，Redis `SET NX EX` 抢锁，只有一台执行；释放锁用 Lua 脚本“先比较再删除”，防止任务执行超过锁时间后误删别人的锁
- **提醒去重**：同一条隐患同一天只提醒一次（Redis 记录），手动多执行几次也不会重复发
- **独立线程池**：调度器默认只有一个线程，一个任务卡住会拖住所有任务（包括消息心跳），所以单独配置了 4 个线程

## 附件

- **用在哪**：隐患现场照片（上报时）、整改后照片（整改时，验收人对照查看）、作业票附件（作业方案、气体检测记录）
- **权限跟随单据**：能看隐患详情的人才能看它的照片；能处理当前环节的人才能上传（例如整改照片只有整改人、并且只在整改中阶段能传）。附件模块定义 `BizAccessChecker` 接口，隐患、作业票模块各自实现，附件模块不需要知道业务规则
- **按文件内容识别类型**：读文件头几个字节（魔数）判断真实格式，只允许 jpg / png / webp / pdf / Word / Excel。把脚本改名成 .jpg 上传会被识别出来拒绝；下载时加 `X-Content-Type-Options: nosniff`
- **存储**：文件名用随机 UUID（不用用户上传的文件名，防止路径穿越、覆盖），按 `企业/年月/` 分目录；存储路径不返回给前端，下载必须经过带权限校验的接口。`FileStorage` 是接口，现在是本地磁盘（Docker 挂数据卷），换 MinIO / OSS 只需要新写一个实现
- **前端显示图片**：`<img>` 标签没法带 Authorization 请求头，所以用 axios 带 token 取回图片再转成 blob 地址显示

## Excel 导入导出

- **导出**（受限空间、隐患、作业票）：直接复用列表查询，**筛选条件、数据权限和列表完全一致**（监护人导出的只有和自己有关的作业票）
- **导入**（受限空间台账）：下载模板（第二页是填写说明）→ 填写 → 上传。**先校验全部行，有任何一行错就一行都不导入**，把所有错误连同 Excel 行号一次性返回；全部正确才在一个事务里批量新增。部门、责任人按名称匹配，重名时提示手动选择
- 表头按列名匹配，用户调整列顺序也能导入

## 数据看板

- 指标卡：作业票数、正在作业（涉及几个受限空间）、未闭环隐患（重大隐患）、逾期未整改、整改及时率、受限空间（高风险）
- 图表：每天新建的作业票（按类型）、作业票状态、隐患上报与闭环趋势、隐患类别、受限空间风险等级；趋势图可以切换成表格查看
- 统计全部在数据库里 `GROUP BY` 聚合，没有数据的日期补 0；SQL 不写 tenant_id，租户插件自动加上
- 配色：作业类型用固定顺序的分类色（同一类型在所有图里颜色不变），风险等级用状态色并配文字；条形图数值直接标在末端

## 工程规范

### 数据库版本管理（Flyway）

表结构变更写成 `db/migration/V{版本号}__说明.sql`，后端**启动时自动执行**还没执行过的版本，执行记录在 `flyway_schema_history` 表。不再需要手动 `docker cp` 执行 SQL。

| 版本 | 内容 |
|---|---|
| V1 | 建表 + 演示数据 |
| V2 | 平台端、登录安全 |
| V3 | 消息通知、定时任务 |
| V4 | 附件、Excel、看板 |
| V5 | 操作日志 trace_id、列表查询索引 |

- 空数据库：从 V1 开始全部执行
- 以前用 init.sql 建好的库：配置了 `baseline-version: 4`，第一次启动只登记“已经到 V4”，然后执行 V5，**原有数据不动**
- 已经执行过的脚本不能再修改（Flyway 会校验文件校验和，改了启动会报错），要改表就新增下一个版本

### 接口文档（springdoc-openapi）

开发环境启动后打开 http://localhost:8080/swagger-ui.html ，按模块分组。先调 `/auth/login` 拿 token，点右上角 **Authorize** 填入，就能直接在页面上调接口。生产环境（prod）关闭文档。

### 请求链路编号（traceId）

- 每个请求生成一个 traceId（Nginx 传入的 `X-Request-Id` 优先），写进日志 MDC，每行日志都带 `[traceId]`
- 响应头返回 `X-Trace-Id`；接口出错时返回体里也有 `traceId`，前端提示“错误编号：xxx”
- 用户反馈问题时报这个编号，在日志里一搜就能找到这次请求的完整日志；操作日志页面也能按错误编号查
- 异步线程（AI 流式回答）通过 `TaskDecorator` 传递 traceId；定时任务生成 `job-` 开头的编号

### 权限缓存

`StpInterfaceImpl` 在一次请求内缓存当前用户的权限、角色。一个接口里多次 `@SaCheckPermission` / 菜单判断只查一次数据库。

### 单元测试

`mvn test` 运行，不需要启动 MySQL、Redis（Redis 用 Mockito 模拟），覆盖核心业务规则：

| 模块 | 测试 |
|---|---|
| 作业票 | 状态机（不能跳步、终态）、气体检测阈值和 30 分钟时效、看板按天补 0 |
| 隐患 | 闭环状态机（验收不通过退回整改） |
| 登录安全 | 失败 5 次锁定、用户名大小写、企业隔离；验证码一次性、生产环境不返回答案 |
| 定时任务 | 分布式锁抢占、失败记录日志 |
| 权限 | 套餐菜单的父子完整性 |
| 附件 | 文件头识别（改扩展名伪装的文件被拒绝） |
| AI | 文本切分、关键词打分、向量计算、JSON 提取、作业票规则预检 |

### 持续集成（GitHub Actions）

每次 push 到 GitHub 自动运行：后端 `mvn verify`（编译 + 全部单元测试），前端 `npm ci && npm run build`。结果看仓库的 **Actions** 页面，README 顶部的徽章显示最近一次是否通过。

## AI 助手

### 知识库问答（RAG）

```
上传 PDF/TXT/MD → 提取文字（PDF 按页）→ 按句子切分（带章节标题、相邻重叠）→ 生成向量 → 存库
提问 → 混合检索（0.7 × 向量相似度 + 0.3 × 关键词）→ 取前 4 段 → 拼提示词 → 大模型流式生成 → 记录
```

- **只根据资料回答**：检索分数低于阈值直接回答“知识库中没有找到”，不调用大模型，从源头避免编造；提示词要求用 [1][2] 标注出处，前端点击可以查看原文
- **混合检索**：向量检索擅长“意思相近、说法不同”，关键词检索擅长编号、数值；只配置对话模型或什么都不配置时，自动退化为关键词检索，系统照常可用
- **多轮追问**：“中断以后呢？”这类追问，把上一个问题拼进来一起检索
- **流式输出（SSE）**：大模型边生成边推送；前端用 `fetch` + `ReadableStream` 解析（`EventSource` 不支持 POST 和自定义请求头）
- **异步线程与租户上下文**：流式回答在独立线程池中执行，ThreadLocal 不会自动传递，所以租户 ID、用户信息在请求线程里先取出，写库时手动设置并清理；同时租户拦截器实现 `AsyncHandlerInterceptor.afterConcurrentHandlingStarted`，避免 Tomcat 线程残留上一个请求的租户
- **成本控制**：Redis 按“用户 + 分钟”限流；历史对话只带最近 6 条并截断；线程池队列满时直接拒绝
- **向量存储**：float32 → Base64 存 MySQL，检索时在内存计算余弦相似度（单企业几千个片段只需几毫秒），按企业缓存索引；规模上来后替换为 pgvector / Milvus 只需改 `RetrievalService`
- **问答记录**：记录每次的问题、回答、引用、耗时；“未命中”的问题可以单独筛选，用来发现知识库缺什么
- **换模型只改配置**：按 OpenAI 兼容协议调用，没有引入 Spring AI 等框架；讯飞星火、通义千问、DeepSeek 都可以直接切换

### 作业票 AI 预审

- **两层检查**：确定性规则（安全措施是否覆盖通风、隔离、检测、防护、救援等要点，气体是否接近限值，检测是否超过 30 分钟，作业是否超过 8 小时）+ 大模型结合检索到的制度条款做补充分析
- 能用规则判断的不交给大模型：规则结果稳定、可解释、零成本；大模型不可用时规则检查照常给出结果
- 要求大模型输出固定 JSON，后端从回复中提取第一个完整 JSON 对象再解析（模型经常在前后加说明文字）
- **AI 只提建议，不改变作业票状态**，是否批准由审批人决定

### 配置 AI（可选）

```powershell
Copy-Item config\secret.example.yml config\secret.yml
notepad config\secret.yml
```

- 对话模型：讯飞开放平台开通 Spark Lite（免费），把 HTTP 服务的 **APIPassword** 填到 `chat.api-key`
- 向量模型：阿里云百炼创建 API Key，填到 `embedding.api-key`
- `config/secret.yml` 已加入 `.gitignore`，不会提交到 GitHub

## 前端设计

- **动态路由**：登录后调用 `/auth/me`，根据后端返回菜单的 `component` 字段，通过 `import.meta.glob` 找到页面组件并 `router.addRoute` 注册；没有权限的页面根本不会注册，直接访问显示 404
- **按钮权限**：自定义指令 `v-perm="'ticket:add'"`，没有权限时移除按钮（只是体验优化，真正的校验在后端）
- **作业票按钮由后端决定**：详情接口返回当前用户可执行的 `actions`，前端不重复写“谁能做什么”的规则，避免前后端规则不一致
- **统一请求封装**：Axios 拦截器自动带 token，统一处理 `{code, msg, data}`，401 自动跳回登录页
- **开发代理**：Vite 把 `/api/*` 转发到 `localhost:8080` 并去掉前缀，部署时由 Nginx 做同样的事

## 演示账号

| 企业编码 | 用户名 | 密码 | 说明 |
|---|---|---|---|
| platform | admin | admin123 | 运营平台 · 平台管理员（开通企业、管理套餐） |
| demo | admin | admin123 | 演示化工 · 企业管理员 |
| demo | zhangsan | admin123 | 演示化工 · 安全员 |
| demo | lisi | admin123 | 演示化工 · 作业负责人 |
| demo | zhaoliu | admin123 | 演示化工 · 监护人 |
| test | admin | admin123 | 测试冶金 · 企业管理员 |
| test | wangwu | admin123 | 测试冶金 · 监护人 |

扫码演示：登录管理员 → 安全管理 → 受限空间 → 二维码 → 在新窗口打开扫码页

## 接口

| 方法 | 路径 | 说明 | 所需权限 |
|---|---|---|---|
| GET | /auth/captcha | 获取图形验证码 | 否 |
| POST | /auth/login | 登录（企业编码、用户名、密码、验证码），返回 token | 否 |
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
| PUT | /system/user/{id}/unlock | 解除密码错误锁定 | system:user:unlock |
| GET | /system/loginlog/page | 本企业登录日志 | system:loginlog:list |
| GET / DELETE | /system/online/list、/system/online/{sessionId} | 在线用户 / 强制下线 | system:online:list、kickout |
| GET | /platform/tenant/page、/{id}、/stats | 企业列表 / 详情 / 平台统计 | platform:tenant:list |
| POST / PUT | /platform/tenant、/{id}、/{id}/status、/{id}/admin-password | 开通 / 修改续费 / 启停 / 重置管理员密码 | platform:tenant:add、edit |
| GET / POST / PUT / DELETE | /platform/package/list、/menu-tree、/platform/package | 套餐管理 | platform:package:* |
| GET | /platform/loginlog/page | 全部企业的登录日志 | platform:loginlog:list |
| GET / POST / PUT | /safety/space/page、/safety/space | 受限空间台账 | safety:space:* |
| GET | /safety/space/{id}/qr-token | 获取二维码令牌 | safety:space:edit |
| GET | /public/space/{token} | 扫码查看（匿名） | 否 |
| GET / POST | /safety/hazard/page、/safety/hazard | 隐患列表 / 上报 | safety:hazard:list、report |
| POST | /safety/hazard/{id}/assign、revoke、verify-pass、verify-fail | 指派 / 撤销 / 验收 | safety:hazard:handle |
| POST | /safety/hazard/{id}/rectify | 提交整改 | 指定整改人 |
| GET | /ai/status | AI 配置状态 | 登录即可 |
| POST | /ai/chat/stream | 知识库问答（SSE：sources → delta… → done） | ai:chat |
| GET | /ai/chat/logs | 问答记录（hit=0 只看未命中） | ai:knowledge:list |
| GET / POST / DELETE | /ai/knowledge/page、/upload、/{id} | 知识库文档 | ai:knowledge:* |
| POST | /ai/knowledge/{id}/embed | 为文档生成向量 | ai:knowledge:upload |
| POST | /ai/ticket/{id}/review | 作业票 AI 预审 | ai:ticket:review |
| GET | /message/stream | 消息实时推送（SSE：unread、message） | 登录即可 |
| GET | /message/page、/unread、/latest | 我的消息 / 未读数 / 最近未读 | 登录即可 |
| PUT | /message/{id}/read、/message/read-all | 标记已读 | 登录即可 |
| GET / POST / DELETE | /system/notice/page、/system/notice、/{id} | 通知公告 列表 / 发布 / 撤回 | system:notice:list、add |
| GET / POST | /platform/job/list、/log/page、/{code}/run | 定时任务 / 执行记录 / 立即执行 | platform:job:list、run |
| GET / POST / DELETE | /file/list、/file/upload、/file/{id} | 附件 列表 / 上传 / 预览下载 / 删除 | 跟随业务单据 |
| GET / POST | /safety/space/export、/import-template、/import | 受限空间 导出 / 模板 / 导入 | safety:space:import |
| GET | /safety/hazard/export、/ticket/export | 导出隐患、作业票台账 | safety:hazard:export、ticket:export |
| GET | /dashboard/overview?days=30 | 数据看板 | dashboard:view |
| GET | /actuator/health | 健康检查 | 否 |

完整接口和参数见在线文档：http://localhost:8080/swagger-ui.html（开发环境）

请求头：`Authorization: Bearer <token>`；时间格式：`yyyy-MM-dd HH:mm:ss`

## 部署

详见 [docs/deploy.md](docs/deploy.md)。

```bash
cp .env.example .env    # 修改密码、填写 AI Key
docker compose -f docker-compose.prod.yml up -d --build
```

- 前端、后端都在容器里编译，服务器只需要安装 Docker
- 只有 Nginx 对外开放 80 端口；MySQL、Redis 不映射端口
- 密码、Key 通过 `.env` 注入环境变量，`.dockerignore` 保证密钥不会打进镜像
- 针对 2G 内存服务器调优：JVM 堆 512M、MySQL 缓冲池 256M、Redis 128M

## 本地运行

```powershell
# 1. 数据库和 Redis
docker compose up -d

# 2. 后端（窗口 1）
mvn clean package          # 会先跑单元测试；只想快速打包可以加 -DskipTests
# 显式指定开发环境：电脑上如果设置了全局环境变量 SPRING_PROFILES_ACTIVE（别的项目留下的），会覆盖默认的 dev
java -jar safework-server\target\safework-server.jar --spring.profiles.active=dev

# 3. 前端（窗口 2），浏览器打开 http://localhost:5173
cd safework-web
npm install
npm run dev

# 4. 接口自测（窗口 3）
powershell -ExecutionPolicy Bypass -File .\test-api.ps1
powershell -ExecutionPolicy Bypass -File .\test-ticket.ps1
powershell -ExecutionPolicy Bypass -File .\test-safety.ps1
powershell -ExecutionPolicy Bypass -File .\test-system.ps1
powershell -ExecutionPolicy Bypass -File .\test-ai.ps1
powershell -ExecutionPolicy Bypass -File .\test-platform.ps1
powershell -ExecutionPolicy Bypass -File .\test-message.ps1
powershell -ExecutionPolicy Bypass -File .\test-file.ps1
```

数据库表结构由 Flyway 在后端启动时自动升级，不需要手动执行 SQL。

