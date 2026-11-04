-- 第二十一部分：安全检查（检查表模板 → 检查计划 → 按周期生成检查任务 → 逐项检查 → 不合格项转隐患）

CREATE TABLE check_template (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    name          VARCHAR(60)   NOT NULL COMMENT '检查表名称',
    check_type    VARCHAR(16)   NOT NULL COMMENT 'DAILY / SPECIAL / HOLIDAY / SEASONAL / COMPREHENSIVE',
    items         TEXT          NOT NULL COMMENT '检查项 JSON：[{content, standard, category}]',
    item_count    INT           NOT NULL DEFAULT 0,
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    remark        VARCHAR(200)  NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全检查表模板';

CREATE TABLE check_plan (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    name           VARCHAR(60)   NOT NULL COMMENT '计划名称',
    template_id    BIGINT        NOT NULL,
    template_name  VARCHAR(60)   NOT NULL,
    check_type     VARCHAR(16)   NOT NULL,
    cycle          VARCHAR(10)   NOT NULL COMMENT 'DAILY / WEEKLY / MONTHLY / ONCE',
    checker_id     BIGINT        NOT NULL COMMENT '检查人',
    checker_name   VARCHAR(50)   NOT NULL,
    location       VARCHAR(100)  NOT NULL COMMENT '检查区域',
    start_date     DATE          NOT NULL,
    end_date       DATE          NULL COMMENT '不填一直执行',
    next_date      DATE          NULL COMMENT '下一个要生成任务的周期开始日期',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    remark         VARCHAR(200)  NULL,
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_next (tenant_id, status, next_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全检查计划';

CREATE TABLE check_task (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    task_no         VARCHAR(30)   NOT NULL COMMENT '编号 JC20261104-0001',
    plan_id         BIGINT        NOT NULL,
    plan_name       VARCHAR(60)   NOT NULL,
    template_name   VARCHAR(60)   NOT NULL,
    check_type      VARCHAR(16)   NOT NULL,
    location        VARCHAR(100)  NOT NULL,
    checker_id      BIGINT        NOT NULL,
    checker_name    VARCHAR(50)   NOT NULL,
    period_start    DATE          NOT NULL COMMENT '周期开始日期',
    due_date        DATE          NOT NULL COMMENT '截止日期',
    status          VARCHAR(10)   NOT NULL COMMENT 'PENDING / DONE',
    items           TEXT          NOT NULL COMMENT '检查项快照 JSON',
    results         TEXT          NULL COMMENT '逐项结果 JSON',
    item_count      INT           NOT NULL DEFAULT 0,
    fail_count      INT           NOT NULL DEFAULT 0,
    hazard_count    INT           NOT NULL DEFAULT 0,
    summary         VARCHAR(500)  NULL,
    finish_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_period (plan_id, period_start, deleted) COMMENT '同一个计划同一个周期只生成一张',
    KEY idx_tenant_checker (tenant_id, checker_id, status, due_date),
    KEY idx_tenant_status (tenant_id, status, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全检查任务';

-- 菜单：安全管理下面加【安全检查】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(309,  3,   '安全检查', 'C', 'T', '/safety/check', 'safety/check/index', 'check:list', 'check', 10, NOW(), NOW()),
(3091, 309, '管理检查表和计划', 'F', 'T', NULL, NULL, 'check:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',309,3091')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('309', menu_ids) = 0;

-- 安全员管理；作业负责人、监护人执行分给自己的检查
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (309, 3091)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 309
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示检查表
INSERT INTO check_template (id, tenant_id, name, check_type, items, item_count, status, remark, create_time, update_time) VALUES
(9601, 1, '罐区日常巡回检查表', 'DAILY',
 '[{"content":"储罐液位、压力、温度在正常范围","standard":"对照 DCS 显示和现场仪表，偏差不超过 5%","category":"设备设施"},{"content":"罐体、管线、阀门无跑冒滴漏","standard":"目视检查，法兰、阀门填料处无渗漏痕迹","category":"设备设施"},{"content":"可燃气体报警器工作正常","standard":"指示灯正常，无故障报警","category":"设备设施"},{"content":"防火堤完好，排水阀处于关闭状态","standard":"堤内无积水、无杂物，雨排阀关闭并挂牌","category":"作业环境"},{"content":"静电接地、防雷设施完好","standard":"接地线连接牢固，无锈蚀断裂","category":"设备设施"},{"content":"消防器材齐全有效","standard":"灭火器压力在绿区，消防栓无遮挡","category":"设备设施"},{"content":"罐区内无动火、无违规使用手机等火源","standard":"进入罐区人员已交出火种，有动火作业的持有效作业票","category":"人员行为"},{"content":"巡检记录、交接班记录填写完整","standard":"上一班记录完整、签名齐全","category":"安全管理"}]',
 8, 1, '每天至少一次，节假日不停', NOW(), NOW()),
(9602, 1, '受限空间作业现场专项检查表', 'SPECIAL',
 '[{"content":"作业票在有效期内，安全措施逐项落实","standard":"现场核对作业票和措施确认签字","category":"安全管理"},{"content":"已进行有效隔离（盲板、断电、挂牌上锁）","standard":"盲板位置与盲板图一致，电源已上锁挂牌","category":"作业环境"},{"content":"已进行通风，气体检测合格且在 30 分钟内","standard":"氧含量 19.5%~23.5%，可燃气体低于 10%LEL","category":"作业环境"},{"content":"监护人在场且持证，不擅离职守","standard":"监护人与作业票一致，携带监护人证","category":"人员行为"},{"content":"救援器材齐全（空气呼吸器、安全绳、三脚架）","standard":"放在入口处 10 米以内，空气呼吸器压力合格","category":"设备设施"},{"content":"作业人员佩戴防护用品，使用安全电压照明","standard":"潮湿环境照明电压不超过 12V","category":"人员行为"}]',
 6, 1, '对正在进行的受限空间作业抽查', NOW(), NOW()),
(9603, 1, '冬季防冻防凝检查表', 'SEASONAL',
 '[{"content":"室外管线、仪表引压管伴热或保温完好","standard":"伴热线通电，保温层无破损","category":"设备设施"},{"content":"停用设备、管线已排净存水","standard":"低点导淋已打开排水","category":"设备设施"},{"content":"消防水系统防冻措施到位","standard":"室外消火栓已排空或保温，消防水泵房温度不低于 5℃","category":"设备设施"},{"content":"装置区、道路积雪结冰及时清理","standard":"主要通道、梯子平台无积冰","category":"作业环境"},{"content":"取暖设施使用符合规定","standard":"禁止在生产区使用电炉、明火取暖","category":"人员行为"},{"content":"防冻防凝应急物资准备齐全","standard":"融雪剂、防滑垫、蒸汽胶管等已备好","category":"安全管理"}]',
 6, 1, '每年入冬前开展', NOW(), NOW());

-- 演示检查计划：李四每天查罐区，赵六每周查受限空间作业现场，张三入冬前查一次防冻防凝
INSERT INTO check_plan (id, tenant_id, name, template_id, template_name, check_type, cycle, checker_id, checker_name, location,
                        start_date, end_date, next_date, status, remark, create_time, update_time) VALUES
(9611, 1, '罐区每日巡回检查', 9601, '罐区日常巡回检查表', 'DAILY', 'DAILY', 103, '李四（作业负责人）', '罐区（1#~4# 储罐）',
 DATE_SUB(CURDATE(), INTERVAL 30 DAY), NULL, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 1, NULL, NOW(), NOW()),
(9612, 1, '受限空间作业现场每周抽查', 9602, '受限空间作业现场专项检查表', 'SPECIAL', 'WEEKLY', 104, '赵六（监护人）', '合成车间、罐区受限空间作业点',
 DATE_SUB(CURDATE(), INTERVAL 3 DAY), NULL, DATE_ADD(CURDATE(), INTERVAL 4 DAY), 1, NULL, NOW(), NOW()),
(9613, 1, '2026 年入冬防冻防凝检查', 9603, '冬季防冻防凝检查表', 'SEASONAL', 'ONCE', 102, '张三（安全员）', '全厂',
 CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), NULL, 1, NULL, NOW(), NOW());

-- 演示检查任务：前天已完成（1 项不合格，现场已处理）、昨天逾期、今天待检查；每周抽查和防冻检查各一张待检查
INSERT INTO check_task (id, tenant_id, task_no, plan_id, plan_name, template_name, check_type, location, checker_id, checker_name,
                        period_start, due_date, status, items, results, item_count, fail_count, hazard_count, summary, finish_time, version, create_time, update_time)
SELECT 9621, 1, CONCAT('JC', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), '%Y%m%d'), '-0001'), 9611, '罐区每日巡回检查', t.name, t.check_type, '罐区（1#~4# 储罐）', 103, '李四（作业负责人）',
       DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'DONE', t.items,
       '[{"content":"储罐液位、压力、温度在正常范围","standard":"对照 DCS 显示和现场仪表，偏差不超过 5%","category":"设备设施","ok":true,"note":null},{"content":"罐体、管线、阀门无跑冒滴漏","standard":"目视检查，法兰、阀门填料处无渗漏痕迹","category":"设备设施","ok":true,"note":null},{"content":"可燃气体报警器工作正常","standard":"指示灯正常，无故障报警","category":"设备设施","ok":true,"note":null},{"content":"防火堤完好，排水阀处于关闭状态","standard":"堤内无积水、无杂物，雨排阀关闭并挂牌","category":"作业环境","ok":false,"note":"3# 罐防火堤雨排阀未关，已当场关闭并挂牌"},{"content":"静电接地、防雷设施完好","standard":"接地线连接牢固，无锈蚀断裂","category":"设备设施","ok":true,"note":null},{"content":"消防器材齐全有效","standard":"灭火器压力在绿区，消防栓无遮挡","category":"设备设施","ok":true,"note":null},{"content":"罐区内无动火、无违规使用手机等火源","standard":"进入罐区人员已交出火种，有动火作业的持有效作业票","category":"人员行为","ok":true,"note":null},{"content":"巡检记录、交接班记录填写完整","standard":"上一班记录完整、签名齐全","category":"安全管理","ok":true,"note":null}]',
       8, 1, 0, '整体正常，雨排阀问题已当场处理', DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 2 DAY), INTERVAL 10 HOUR), 1, NOW(), NOW()
FROM check_template t WHERE t.id = 9601;

INSERT INTO check_task (id, tenant_id, task_no, plan_id, plan_name, template_name, check_type, location, checker_id, checker_name,
                        period_start, due_date, status, items, item_count, version, create_time, update_time)
SELECT 9622, 1, CONCAT('JC', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '-0001'), 9611, '罐区每日巡回检查', t.name, t.check_type, '罐区（1#~4# 储罐）', 103, '李四（作业负责人）',
       DATE_SUB(CURDATE(), INTERVAL 1 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'PENDING', t.items, t.item_count, 0, NOW(), NOW()
FROM check_template t WHERE t.id = 9601
UNION ALL
SELECT 9623, 1, CONCAT('JC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-0901'), 9611, '罐区每日巡回检查', t.name, t.check_type, '罐区（1#~4# 储罐）', 103, '李四（作业负责人）',
       CURDATE(), CURDATE(), 'PENDING', t.items, t.item_count, 0, NOW(), NOW()
FROM check_template t WHERE t.id = 9601
UNION ALL
SELECT 9624, 1, CONCAT('JC', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '%Y%m%d'), '-0901'), 9612, '受限空间作业现场每周抽查', t.name, t.check_type, '合成车间、罐区受限空间作业点', 104, '赵六（监护人）',
       DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'PENDING', t.items, t.item_count, 0, NOW(), NOW()
FROM check_template t WHERE t.id = 9602
UNION ALL
SELECT 9625, 1, CONCAT('JC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-0902'), 9613, '2026 年入冬防冻防凝检查', t.name, t.check_type, '全厂', 102, '张三（安全员）',
       CURDATE(), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'PENDING', t.items, t.item_count, 0, NOW(), NOW()
FROM check_template t WHERE t.id = 9603;
