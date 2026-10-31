-- 第十六部分：应急管理（应急预案、事故快报与应急响应、应急演练）

CREATE TABLE emergency_plan (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    plan_code       VARCHAR(30)   NOT NULL COMMENT '预案编号',
    title           VARCHAR(60)   NOT NULL,
    plan_type       VARCHAR(20)   NOT NULL COMMENT 'COMPREHENSIVE 综合 / SPECIAL 专项 / ONSITE 现场处置方案',
    accident_type   VARCHAR(20)   NOT NULL COMMENT '适用的事故类型',
    scope           VARCHAR(200)  NULL COMMENT '适用范围',
    content         TEXT          NOT NULL COMMENT '应急处置程序',
    team_user_ids   VARCHAR(500)  NULL COMMENT '应急小组成员 ID，逗号分隔',
    team_names      VARCHAR(500)  NULL,
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    last_drill_time DATETIME      NULL COMMENT '最近一次演练时间',
    updater_name    VARCHAR(50)   NULL,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, plan_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应急预案';

CREATE TABLE emergency_event (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    event_no        VARCHAR(30)   NOT NULL,
    title           VARCHAR(100)  NOT NULL,
    accident_type   VARCHAR(20)   NOT NULL,
    level           VARCHAR(10)   NOT NULL COMMENT 'GENERAL / LARGER / MAJOR / EXTRA',
    location        VARCHAR(100)  NOT NULL,
    space_id        BIGINT        NULL,
    space_name      VARCHAR(100)  NULL,
    description     VARCHAR(1000) NULL,
    dead            INT           NOT NULL DEFAULT 0,
    serious_injured INT           NOT NULL DEFAULT 0,
    minor_injured   INT           NOT NULL DEFAULT 0,
    occur_time      DATETIME      NOT NULL,
    reporter_id     BIGINT        NOT NULL,
    reporter_name   VARCHAR(50)   NULL,
    status          VARCHAR(20)   NOT NULL COMMENT 'REPORTED 已上报 / RESPONDING 响应中 / CLOSED 已结束',
    plan_id         BIGINT        NULL COMMENT '启动的预案',
    plan_title      VARCHAR(60)   NULL,
    commander_id    BIGINT        NULL COMMENT '现场指挥（启动响应的人）',
    commander_name  VARCHAR(50)   NULL,
    response_time   DATETIME      NULL,
    close_time      DATETIME      NULL,
    summary         VARCHAR(2000) NULL COMMENT '事后总结',
    version         INT           NOT NULL DEFAULT 0,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status, occur_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='突发事件 / 事故快报';

CREATE TABLE emergency_event_log (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    event_id      BIGINT        NOT NULL,
    action        VARCHAR(20)   NOT NULL COMMENT 'REPORT / START / UPDATE / CLOSE',
    content       VARCHAR(2000) NULL,
    operator_id   BIGINT        NOT NULL,
    operator_name VARCHAR(50)   NULL,
    create_time   DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_event (tenant_id, event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应急事件处置记录';

CREATE TABLE emergency_drill (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '企业',
    plan_id           BIGINT        NOT NULL,
    plan_title        VARCHAR(60)   NULL,
    title             VARCHAR(60)   NOT NULL,
    drill_type        VARCHAR(10)   NOT NULL COMMENT 'TABLETOP 桌面推演 / LIVE 实战演练',
    plan_time         DATETIME      NOT NULL,
    location          VARCHAR(100)  NULL,
    status            VARCHAR(10)   NOT NULL COMMENT 'PLANNED / DONE / CANCELLED',
    actual_time       DATETIME      NULL,
    participant_count INT           NULL,
    summary           VARCHAR(2000) NULL,
    problems          VARCHAR(2000) NULL,
    recorder_name     VARCHAR(50)   NULL,
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_plan (tenant_id, plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应急演练';

-- 菜单：安全管理下面加【应急事件】（所有人可以快报）和【应急预案】（含演练）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(304,  3,   '应急事件', 'C', 'T', '/safety/emergency',      'safety/emergency/index',      'emergency:event:list', 'emergency',      5, NOW(), NOW()),
(3041, 304, '事故快报', 'F', 'T', NULL, NULL, 'emergency:event:report', NULL, 1, NOW(), NOW()),
(3042, 304, '应急处置', 'F', 'T', NULL, NULL, 'emergency:event:handle', NULL, 2, NOW(), NOW()),
(305,  3,   '应急预案', 'C', 'T', '/safety/emergency-plan', 'safety/emergency-plan/index', 'emergency:plan:list',  'emergency-plan', 6, NOW(), NOW()),
(3051, 305, '编辑预案和演练', 'F', 'T', NULL, NULL, 'emergency:plan:edit', NULL, 1, NOW(), NOW());

-- 套餐：有安全管理的都加上
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',304,3041,3042,305,3051')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('304', menu_ids) = 0;

-- 角色：安全员全部；作业负责人、监护人可以查看、快报、查看预案（每个人都应该知道出事了怎么办）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (304, 3041, 3042, 305, 3051)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (304, 3041, 305)
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示数据：演示化工 4 个预案（综合、专项、两个现场处置方案）
INSERT INTO emergency_plan (id, tenant_id, plan_code, title, plan_type, accident_type, scope, content, team_user_ids, team_names, status, last_drill_time, updater_name, create_time, update_time) VALUES
(9101, 1, 'YA-ZH-01', '生产安全事故综合应急预案', 'COMPREHENSIVE', 'OTHER', '全厂范围内各类生产安全事故',
'一、事故发现人立即向调度室和本部门负责人报告，说明时间、地点、事故类型、伤亡情况。
二、调度室接报后立即通知应急领导小组，由现场最高职位的管理人员担任现场指挥。
三、根据事故等级启动相应的专项预案或现场处置方案。
四、设置警戒区，疏散无关人员，引导救援车辆进入。
五、较大及以上事故，1 小时内向当地应急管理部门报告。
六、事故得到控制后，保护现场，配合事故调查，组织恢复生产。
严禁迟报、漏报、谎报、瞒报事故。',
'101,102', '演示企业管理员、张三（安全员）', 1, NULL, '演示企业管理员', NOW(), NOW()),
(9102, 1, 'YA-XC-01', '受限空间中毒窒息现场处置方案', 'ONSITE', 'POISONING', '储罐、反应釜、污水池、地下管沟等受限空间作业',
'一、监护人发现作业人员晕倒或无应答，立即大声呼救，并报告调度室和安全员。
二、严禁未佩戴正压式空气呼吸器的人员进入受限空间施救，防止救援人员连续中毒。
三、立即加强通风，切断可能的有毒气体来源。
四、救援人员佩戴正压式空气呼吸器、系好安全绳，在监护下进入，用三脚架将伤员救出。
五、将伤员移到空气新鲜处，解开衣领，呼吸心跳停止的立即进行心肺复苏。
六、拨打 120，安排专人到路口引导救护车。
七、救援结束后检测气体，查明原因前不得恢复作业。',
'102,104', '张三（安全员）、赵六（监护人）', 1, '2026-04-15 09:30:00', '张三（安全员）', NOW(), NOW()),
(9103, 1, 'YA-ZX-01', '储罐区危化品泄漏及火灾爆炸专项应急预案', 'SPECIAL', 'LEAK', '储罐区、装卸站，甲醇等易燃液体储罐',
'一、发现泄漏立即报告调度室，说明泄漏位置、介质和泄漏量。
二、在保证安全的前提下关闭泄漏点上下游阀门，停止装卸作业。
三、设置警戒区，严禁一切火源，严禁使用非防爆工具和手机，切断警戒区内非防爆电源。
四、疏散下风向人员，现场人员佩戴防毒面具。
五、启动储罐喷淋冷却，用围堰、沙袋收集泄漏液体，防止流入下水道。
六、发生火灾时使用抗溶性泡沫灭火，同时冷却相邻储罐，拨打 119。
七、泄漏控制后检测可燃气体浓度，合格后方可解除警戒。',
'101,102,103', '演示企业管理员、张三（安全员）、李四（作业负责人）', 1, '2026-03-20 14:00:00', '张三（安全员）', NOW(), NOW()),
(9104, 1, 'YA-XC-02', '高处坠落现场处置方案', 'ONSITE', 'FALL', '屋顶、平台、脚手架等 2 米以上的高处作业',
'一、立即停止作业，监护人呼救并报告安全员。
二、不要随意搬动伤员，怀疑脊柱损伤时保持伤员平躺不动。
三、对出血部位加压包扎止血，骨折部位用木板临时固定。
四、拨打 120，说明坠落高度和伤员情况。
五、在坠落区域设置警戒，保护现场，检查安全带、护栏等防护设施。
严禁在原因查明、防护设施恢复前继续高处作业。',
'102,103', '张三（安全员）、李四（作业负责人）', 1, NULL, '张三（安全员）', NOW(), NOW()),
-- 测试冶金：租户隔离测试用
(9201, 2, 'YA-XC-01', '转炉煤气中毒现场处置方案', 'ONSITE', 'POISONING', '转炉烟道、煤气管道检修',
'一、发现有人煤气中毒，立即呼救并报告调度室。
二、严禁未佩戴空气呼吸器进入煤气区域施救。
三、将中毒人员移到上风向空气新鲜处，拨打 120。',
NULL, NULL, 1, NULL, '测试冶金管理员', NOW(), NOW());

-- 演练：受限空间方案半年前演练过（已逾期），储罐区专项预案今年演练过，高处坠落方案安排了一次
INSERT INTO emergency_drill (id, tenant_id, plan_id, plan_title, title, drill_type, plan_time, location, status, actual_time, participant_count, summary, problems, recorder_name, create_time, update_time) VALUES
(9301, 1, 9102, '受限空间中毒窒息现场处置方案', '污水池受限空间救援实战演练', 'LIVE', '2026-04-15 09:00:00', '污水处理站调节池', 'DONE', '2026-04-15 09:30:00', 12,
 '模拟清淤人员晕倒，监护人发现后 1 分钟内呼救，救援人员佩戴空气呼吸器 6 分钟内将“伤员”救出，整体流程顺畅。', '三脚架展开速度慢；部分人员空气呼吸器气瓶压力检查不熟练。', '张三（安全员）', NOW(), NOW()),
(9302, 1, 9103, '储罐区危化品泄漏及火灾爆炸专项应急预案', '甲醇储罐泄漏桌面推演', 'TABLETOP', '2026-03-20 14:00:00', '三楼会议室', 'DONE', '2026-03-20 14:00:00', 18,
 '按预案推演从发现泄漏到解除警戒全过程，明确了各岗位职责。', '下风向疏散路线标识不清，需要补充。', '张三（安全员）', NOW(), NOW()),
(9303, 1, 9104, '高处坠落现场处置方案', '厂房屋顶高处坠落救援演练', 'LIVE', DATE_ADD(CURDATE(), INTERVAL 12 DAY) + INTERVAL 9 HOUR, '1 号厂房', 'PLANNED', NULL, NULL, NULL, NULL, NULL, NOW(), NOW());

-- 历史事件：一次已经处置结束的储罐小泄漏（展示完整的处置时间线）
INSERT INTO emergency_event (id, tenant_id, event_no, title, accident_type, level, location, space_id, space_name, description, dead, serious_injured, minor_injured, occur_time, reporter_id, reporter_name, status, plan_id, plan_title, commander_id, commander_name, response_time, close_time, summary, version, create_time, update_time) VALUES
(9401, 1, 'YJ20261015-0001', 'T-102 储罐底部法兰甲醇渗漏', 'LEAK', 'GENERAL', '罐区东侧 2# 原料储罐 T-102', 1002, '2# 原料储罐 T-102',
 '巡检发现储罐底部出料法兰处有甲醇滴漏，地面有约 1 平方米液渍，现场有明显气味，无人员伤亡。', 0, 0, 0,
 '2026-10-15 08:40:00', 103, '李四（作业负责人）', 'CLOSED', 9103, '储罐区危化品泄漏及火灾爆炸专项应急预案', 102, '张三（安全员）',
 '2026-10-15 08:46:00', '2026-10-15 10:20:00',
 '法兰垫片老化导致渗漏。关闭出料阀、倒罐后更换垫片，泄漏液体用沙土吸附后按危废处置。整改：储罐法兰垫片纳入每季度检查，已作为隐患登记跟踪。', 3, NOW(), NOW());
INSERT INTO emergency_event_log (id, tenant_id, event_id, action, content, operator_id, operator_name, create_time) VALUES
(9501, 1, 9401, 'REPORT', '暂无人员伤亡', 103, '李四（作业负责人）', '2026-10-15 08:41:00'),
(9502, 1, 9401, 'START', '启动《储罐区危化品泄漏及火灾爆炸专项应急预案》，现场指挥：张三（安全员）。罐区设置警戒，停止装卸', 102, '张三（安全员）', '2026-10-15 08:46:00'),
(9503, 1, 9401, 'UPDATE', '已关闭 T-102 出料阀，泄漏停止；可燃气体检测 3%LEL', 102, '张三（安全员）', '2026-10-15 09:05:00'),
(9504, 1, 9401, 'UPDATE', '泄漏液体已用沙土吸附清理，开始倒罐', 103, '李四（作业负责人）', '2026-10-15 09:40:00'),
(9505, 1, 9401, 'CLOSE', '法兰垫片老化导致渗漏。关闭出料阀、倒罐后更换垫片，泄漏液体用沙土吸附后按危废处置。整改：储罐法兰垫片纳入每季度检查，已作为隐患登记跟踪。', 102, '张三（安全员）', '2026-10-15 10:20:00');
