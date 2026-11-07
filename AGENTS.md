# AGENTS.md：给接手这个项目的 AI 编程助手（Codex 等）

> 这份文件由之前负责这个项目的 AI（Claude）写的交接说明。**每次开始工作前先读完它**，再读 `README.md`（功能和设计的完整说明）。

---

## 1. 用户是谁、怎么和他沟通（最重要）

- 用户：钱京国，计算机专业应届生（2026 年 6 月毕业），在合肥。这个项目是他求职（科大讯飞等，Java 开发 / AI 应用开发方向）的**作品集项目**，面试时要能讲清楚、能现场演示。
- 他自称**编程初学者**。所有回复：
  - **用中文**，一步一步来，每一步说清楚在哪里执行、执行后应该看到什么。
  - 给**可以直接复制运行的命令**。在他电脑上是 **Windows PowerShell**（提示符 `PS D:\code\safework>`）；在服务器上是 **bash**（提示符 `root@iZuf6bzzdbx6653cjwt6zxZ:/opt/safework#`）。命令前标明【电脑】或【服务器】，不要混淆（他曾经把服务器命令贴到 PowerShell 里）。
  - 服务器上的命令**一行一行给**，提醒他一次只粘贴一行（见第 7 节“坑”）。
  - 改了什么、为什么改，用一两句大白话解释；不要一次给太多选择。
  - 出错时让他“把输出发给我”，根据输出定位问题，不要猜。
- 他的工作流习惯：
  - 说“继续写下一个模块”时，先给 2～4 个候选模块让他选（把推荐的放第一个），他通常选推荐的。
  - 每个模块做完，他会：本地编译 → 跑 PS1 测试脚本 → 手动测试 → 提交 GitHub → 打包部署到服务器 → 手机上看一遍。
  - 电脑经常重启，会问“项目怎么重新启动”（见第 5 节）。

## 2. 安全红线（绝对不能违反）

- `config/secret.yml`（大模型、讯飞的 Key）和 `.env`（服务器上的数据库密码、微信推送 SendKey）**永远不能提交到 Git**。提交前让他运行 `git check-ignore -v config/secret.yml .env`，两个都要显示被忽略。
- **不要在回复里打印任何密钥**；需要检查配置时只检查“有没有填”，不显示内容。提醒他截图时遮住密钥。
- 生产环境（prod）**不能返回验证码答案**（只有 dev 环境返回，测试脚本靠它自动登录）。

## 3. 项目概况

**SafeWork 智能安全作业管理平台**：面向化工、工贸企业的多租户 SaaS。特殊作业票审批、隐患排查闭环、风险分级管控、安全检查、应急管理、人员资质、承包商、设备设施、培训考试、AI 助手（RAG 问答、智能体、语音上报）、安全大屏、手机端 H5、SaaS 平台端。

| 位置 | 说明 |
|---|---|
| 他的电脑 | `D:\code\safework`（Windows，项目根目录） |
| GitHub | 用户名 `qianjingguo666-lang`，有 GitHub Actions CI |
| 服务器 | 阿里云 ECS `47.117.146.55`（Ubuntu 24.04，2 核 2G），部署目录 `/opt/safework`，http://47.117.146.55 是公开演示站（`DEMO_MODE=true`） |
| 域名 | `qianjingguo.asia` 已买，**等 ICP 备案**通过后再绑定和配 HTTPS（之后浏览器才允许手机端用麦克风做语音输入） |

### 技术栈

- 后端：Java 17、Spring Boot 3.3.5、MyBatis-Plus 3.5.7（租户插件、逻辑删除、乐观锁）、Sa-Token、MySQL 8、Redis 7、Flyway、springdoc
- 前端：Vue 3 + Vite + Element Plus + Pinia + ECharts（`safework-web`），手机端 H5 在同一个项目里（路由 `/m`）
- 部署：Docker Compose（mysql、redis、backend、web/Nginx），发布包方式部署

### Maven 模块（依赖方向）

```
safework-common ← safework-system ← safework-safety ← safework-ticket ← safework-ai ← safework-server（启动类、配置、Flyway 脚本）
                                                    ↖ safework-training ↗
```
- `safework-safety`：受限空间、隐患、风险、应急、资质(cert)、承包商(contractor)、设备(equipment)、安全检查(check)、劳保用品(ppe)、职业健康(health，岗位复用 ppe_post)、事故调查(accident，结案后通过 Spring 事件让 safework-ai 的 AccidentCaseListener 写知识库)、危险化学品(chem，重大危险源辨识 GB 18218)、变更管理(moc，状态机 MocRules.Action + 审批轮次)、班前会(meeting；AI 起草要点在 safework-ai 的 MeetingTipService)、工伤管理(injury，状态机 InjuryRules.Action，30 日申请认定倒计时)
- `safework-ticket`：作业票、审批流、打印验真、大屏(screen)、手机首页(mobile)、安全生产责任制(resp，岗位复用 ppe_post)、安全目标考核(kpi，依赖 safety + training，从各模块自动统计)、标准化自评(std，从各模块取证)、外来人员入厂(visit，复用 sign 包的签名校验)
- 数据库脚本：`safework-server/src/main/resources/db/migration/V1~V29`

## 4. 代码约定（新代码照着现有模块写）

参考最新、最完整的模块：`safework-safety/.../health/`（职业健康）、`.../ppe/`（劳保用品）、`.../check/`（安全检查）和 `.../equipment/`（设备设施）。

1. **规则写成纯函数类** `XxxRules`（无 Spring 依赖），配单元测试 `XxxRulesTest`；Service 调用它。状态流转集中在一个入口（作业票 `TicketService.fire()`、隐患 `HazardAction` 枚举），不允许直接改状态。
2. **多租户**：业务表都有 `tenant_id`，SQL 里**不要手写租户条件**，租户插件自动加。定时任务没有登录上下文，用 `TenantJobHelper.forEachActiveTenant(...)` 按企业逐个执行。
3. **权限两层**：接口上 `@SaCheckPermission("xxx:yyy")` 管“有没有这个功能”；Service 里判断“是不是本人的单据”。越权查看返回 404（不暴露数据是否存在）。
4. **Long 会被序列化成字符串**（全局配置，防止 JS 丢精度）。返回给前端做加减比较的**计数用 Integer**；PS1 测试里比较数字要先 `[int]` 转换。
5. **Flyway**：已执行的脚本**绝对不能修改**（会校验失败启动不了），改表就新增下一个版本。**下一个版本号是 V31**。
6. **菜单 ID**：顶级 2 作业管理、3 安全管理、4 AI、6 数据看板、7 安全培训、8 安全大屏；安全管理下面已用到 **322**（工伤管理，排序 19；外来人员入厂排序 18；随手拍积分排序 2；安全目标、安全投入、标准化自评排序都是 0，放在最前，同排序按 ID），新页面从 **323** 开始，按钮 ID 是页面 ID×10+序号（如 3231）。新菜单要：迁移脚本插入 `sys_menu` + 更新 `sys_tenant_package.menu_ids` + 给已有企业的角色插 `sys_role_menu`，同时更新 `PresetRoles.java`（新开通企业的预置角色）。前端左侧菜单图标在 `Layout.vue` 的 `ICONS` 里加。
7. **消息通知**：`MessageService.send(...)`；定时任务里用 `sendOnce(去重key, 时间窗, ...)` 防止重复提醒。消息 `link` 用电脑端地址（如 `/safety/check?detail=ID`），手机端在 `utils/mobile.js` 的 `mobileLink()` 里映射。
8. **定时任务**：实现 `ScheduledJob` 接口自动注册。现在共 **23 个**任务；新增任务要同步改 `test-message.ps1` 里的 `$expectJobs`。**定时任务线程里没有登录用户**：任务调用的 service 方法不能用 `StpUtil`（会抛未登录异常），需要时加参数绕开（见 `ChemService.overstocked`）。
9. **附件**：新业务类型实现 `BizAccessChecker`（参考 `CheckAccessChecker`）。
10. **跨模块复用业务方法**：例如“不合格转隐患”统一调用 `HazardService.report(...)`，不要直接往隐患表插数据。
11. **可选请求体的 POST 接口**：Spring 对空请求体会返回 415。参考 `TicketController.submit`：一个 `consumes = JSON` 且 `@RequestBody` 必填的方法 + 一个不带请求体的方法。
12. **演示数据迁移**：日期一律相对 `CURDATE()`/`NOW()` 写；不同迁移脚本执行的日子可能差几天，和已有数据按日期唯一的表（`check_task` 的 `plan_id + period_start`、`shift_meeting` 的 `team_id + meeting_date + shift`）插入时要加 `NOT EXISTS` 跳过已有日期（见 V26）。
13. **HTTPS 在服务器上单独配置**：`/opt/safework/docker-compose.override.yml` + `nginx-https.conf`（项目里备份在 `deploy/https/`），发布包不包含它们，部署不会覆盖；服务器上的健康检查都要用 `https://127.0.0.1/...` 加 `-k`（http 会被 301 跳转）。
14. **交付方式**：他也会在别处（Codex、直接改服务器）改代码，开发前先从 GitHub（`qianjingguo666-lang/safework`）拉最新代码；交付时只发改动过的文件，不要发整包让他覆盖解压。
15. **手动恢复演示数据要写日志**：服务器上手动运行用 `bash demo-reset.sh 2>&1 | tee -a demo/reset.log`。看门狗读 `demo/reset.log` 的最后几行判断恢复是否成功，直接 `bash demo-reset.sh` 的输出不进日志，会一直报“最近一次演示数据恢复没有成功”。
12. 前端：**日期范围选择器（`type="datetimerange"` / `daterange`）一律加 `:popper-options="PICKER_POPPER"`**（`src/utils/picker.js`），否则笔记本 125% 缩放时面板超出屏幕、点不到“确定”（用户实际遇到过）。API 都在 `src/api/index.js`；字典在 `src/utils/dict.js`（**注意名字别重复**，`CHECK_STATES` 已被风险巡检占用，安全检查用的是 `CHECK_TASK_STATES`）；页面放 `src/views/<模块>/index.vue`，由后端菜单的 `component` 字段动态注册路由。

### 每个新模块的完成清单

- [ ] 迁移脚本 V{n}（表 + 菜单 + 套餐 + 角色权限 + 演示数据）
- [ ] Rules 纯函数 + 单元测试
- [ ] 实体、Mapper、DTO、Service、Controller（`@OperLog` 记录操作日志）
- [ ] PresetRoles、Layout 图标
- [ ] 前端电脑页面；需要现场用的功能加手机页面
- [ ] 视情况接入：消息、定时任务、安全大屏预警、手机首页待办、智能体工具
- [ ] PS1 自测脚本 `test-xxx.ps1`（**UTF-8 带 BOM + CRLF 换行**，参考 `test-check.ps1` 的写法）
- [ ] 更新 README（进度表、模块说明、Flyway 版本表、定时任务表、接口表、测试列表）

## 5. 本地运行（他电脑重启后最常问）

```powershell
# 0. 打开 Docker Desktop，等左下角显示 Engine running
# 1. 窗口 1：数据库和后端
cd D:\code\safework
docker compose up -d
mvn -q clean package -DskipTests        # 代码有改动时才需要
java -jar safework-server\target\safework-server.jar --spring.profiles.active=dev
# 2. 窗口 2：前端，浏览器打开 http://localhost:5173（手机版 /m，F12 再 Ctrl+Shift+M）
cd D:\code\safework\safework-web
npm run dev
# 3. 窗口 3：测试
powershell -ExecutionPolicy Bypass -File .\test-check.ps1
```

- 编译前先停后端：`Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force`，否则 jar 被占用，`mvn clean` 报 “Failed to delete … safework-server.jar”。
- 演示账号（密码都是 `admin123`）：企业编码 `demo` 下 admin（企业管理员）、zhangsan（安全员）、lisi（作业负责人）、zhaoliu（监护人）；`test`/admin（另一家企业，测租户隔离）；`platform`/admin（平台管理员）。
- 接口文档：http://localhost:8080/swagger-ui.html

## 6. 发布到服务器（完整流程）

```powershell
# 【电脑】提交
cd D:\code\safework
git check-ignore -v config/secret.yml .env
git add .
git commit -m "说明"
git push
# 【电脑】打包上传（先停本地后端）。发布包在 D:\code\ 下，不在项目目录里
powershell -ExecutionPolicy Bypass -File deploy\pack.ps1
scp "D:\code\safework-release.tar.gz" root@47.117.146.55:/root/
ssh root@47.117.146.55
```
```bash
# 【服务器】一行一行粘贴
cd /opt/safework
docker compose stop backend                     # 服务器内存只有 1.6G：先停后端腾内存，否则备份时会卡死、SSH 断开
nohup bash update.sh > update.log 2>&1 &        # 后台跑，SSH 断了也会继续（2026-11 出现过前台跑时 SSH 断开、更新中断）
tail -f update.log                              # 看进度，出现“更新完成”即可；Ctrl+C 只是退出查看
# 这次新增了演示数据时，再执行（一行一行）：
bash demo-reset.sh      # 等“恢复完成”
bash demo-baseline.sh   # 保存新的演示数据基线（每天 4:00 按它恢复）
```

### 服务器上的运维（已经配好）

- crontab：`0 3` 数据库备份（`backup.sh`，保留 7 天）、`0 4` 演示数据恢复（`demo-reset.sh`）、`*/2` 看门狗（`watchdog.sh`）。
- 看门狗：每 2 分钟检查网站、后端健康、4 个容器、磁盘、内存、备份；后端连续 2 次不通自动重启；通过 Server酱推送到他的微信（`.env` 里的 `WECHAT_SENDKEY`）；每天 9 点日报。`bash watchdog.sh status` 看状态，`bash watchdog.sh test` 发测试消息。
- 更新期间看门狗自动跳过检查，不会误报。

## 7. 踩过的坑（别再踩）

- **服务器上一次粘贴多行命令会丢命令**：`docker compose exec` 会读走标准输入，把后面粘贴的行“吃掉”（导致解压根本没执行、服务器没更新，发生过三次）。脚本里已加 `</dev/null`，但仍然让他一行一行粘贴。
- PS1 脚本：不要用 `$pid`、`$home`、`$host`、`$input`、`$args` 做变量名（PowerShell 自动变量，有的只读）；也不要给 `$base` 重新赋值（测试脚本开头用它存后端地址，覆盖后会报“未能解析此远程名称 system.collections.hashtable”）；`(表达式).Count -ge 1` 要整体加括号；PowerShell 5.1 里别用 `@(...)[-1]` 做比较，改成比较求和。
- 用 Python 等工具改 PS1 文件时会把 CRLF 变成 LF，改完要转回 CRLF + BOM。
- `docker compose` 在 PowerShell 里不要 `cd /opt/...`（那是服务器路径）。
- 前端打包产物、`node_modules`、`target` 不要提交。

## 8. 已完成的功能（截至 2026-11-05）

登录安全与多租户、RBAC、作业票（多级审批、手写签名、打印验真、AI 预审）、受限空间与扫码、隐患闭环（语音 + AI 填表）、风险分级管控与巡检、应急管理、人员资质、承包商、设备设施、安全检查（V16）、劳保用品（V17）、职业健康（V18）、安全生产责任制（V19，代码在 safework-ticket 的 resp 包，因为考核要统计作业票）、事故调查“四不放过”（V20）、危险化学品与重大危险源（V21）、变更管理 MOC（V22）、班前会（V23）、智能体新工具（危化品、事故、变更、班前会、责任书）、安全目标与绩效考核（V24）、安全投入台账（V25，代码在 safework-safety 的 cost 包；智能体现在 18 个查询工具 + 3 个操作）、演示数据补充（V26：安全目标页面绿 2、黄 4、红 2）、安全生产标准化自评（V27，代码在 safework-ticket 的 std 包，因为要从各模块取证）、隐患随手拍积分（V28，safework-safety 的 point 包；隐患流转发布 HazardFiredEvent，积分模块事务提交后监听加分）、外来人员与车辆入厂（V29，safework-ticket 的 visit 包，复用作业票的手写签名校验）、**工伤管理（V30，最新，safework-safety 的 injury 包；智能体现在 19 个查询工具 + 3 个操作）**、培训考试与 AI 出题、AI 知识库问答与智能体、消息与定时任务、数据看板、安全大屏、手机端 H5、SaaS 平台端、演示模式、Docker 部署、备份、看门狗微信告警。

详细设计全部写在 `README.md` 里。面试用的手动测试手册他在 Claude 里有一份文档。

## 9. 可以做的下一步（等他选）

1. **更新简历**：把“安全检查”“手机端 H5”“看门狗告警”写进 SafeWork 项目经历（他的简历现在是 v4，PDF）。
2. **下一步候选**：更新简历（v4 之后加了 10 个模块 + 智能体新工具）；面试问答整理。
3. **已完成**：备案通过，`https://qianjingguo.asia` 已上线。原计划：绑定域名 `qianjingguo.asia` + HTTPS（Nginx + 证书），然后把简历和演示地址换成域名。
4. 可选增强：给智能体加 `search_check_tasks`、`search_ppe` 工具（参考 `EquipmentSearchTool`）。
