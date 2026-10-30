-- 第十三部分：风险分级管控（双重预防机制：风险分级管控 + 隐患排查治理）

CREATE TABLE risk_point (
    id                 BIGINT        NOT NULL COMMENT '主键',
    tenant_id          BIGINT        NOT NULL COMMENT '企业',
    point_code         VARCHAR(20)   NOT NULL COMMENT '风险点编号',
    point_name         VARCHAR(50)   NOT NULL COMMENT '风险点名称',
    point_type         VARCHAR(20)   NOT NULL COMMENT '设备设施 / 作业活动 / 区域场所',
    location           VARCHAR(100)  NOT NULL,
    dept_id            BIGINT        NULL COMMENT '责任部门',
    responsible_id     BIGINT        NOT NULL COMMENT '责任人',
    responsible_name   VARCHAR(50)   NULL,
    space_id           BIGINT        NULL COMMENT '关联受限空间',
    space_name         VARCHAR(50)   NULL,
    hazard_factors     VARCHAR(500)  NULL COMMENT '危险有害因素',
    possible_accidents VARCHAR(200)  NULL COMMENT '可能导致的事故',
    lec_l              DECIMAL(5,1)  NOT NULL COMMENT 'L 可能性',
    lec_e              DECIMAL(5,1)  NOT NULL COMMENT 'E 暴露频繁程度',
    lec_c              DECIMAL(5,1)  NOT NULL COMMENT 'C 后果',
    lec_d              DECIMAL(8,1)  NOT NULL COMMENT 'D = L × E × C',
    risk_level         VARCHAR(10)   NOT NULL COMMENT 'MAJOR 重大（红）HIGH 较大（橙）GENERAL 一般（黄）LOW 低（蓝）',
    control_measures   TEXT          NOT NULL COMMENT '管控措施，一行一条',
    check_cycle_days   INT           NOT NULL COMMENT '巡检周期（天）',
    last_check_time    DATETIME      NULL,
    next_check_time    DATETIME      NULL,
    status             TINYINT       NOT NULL DEFAULT 1,
    create_time        DATETIME      NULL,
    update_time        DATETIME      NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_level (tenant_id, risk_level),
    KEY idx_tenant_next (tenant_id, status, next_check_time),
    KEY idx_responsible (tenant_id, responsible_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险点';

CREATE TABLE risk_check (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    point_id      BIGINT        NOT NULL,
    point_code    VARCHAR(20)   NULL,
    point_name    VARCHAR(50)   NULL,
    risk_level    VARCHAR(10)   NULL,
    checker_id    BIGINT        NOT NULL,
    checker_name  VARCHAR(50)   NULL,
    check_time    DATETIME      NOT NULL,
    result        VARCHAR(10)   NOT NULL COMMENT 'NORMAL 正常 / ABNORMAL 有措施失效',
    items         TEXT          NULL COMMENT '逐条检查结果（JSON）',
    remark        VARCHAR(500)  NULL,
    hazard_id     BIGINT        NULL COMMENT '转成的隐患',
    hazard_no     VARCHAR(30)   NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_point (tenant_id, point_id, check_time),
    KEY idx_tenant_time (tenant_id, check_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险点巡检记录';

-- 菜单：安全管理下面加【风险分级管控】（安全员）和【风险巡检】（所有责任人）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(302,  3,   '风险分级管控', 'C', 'T', '/safety/risk',       'safety/risk/index',       'risk:point:list', 'risk',       3, NOW(), NOW()),
(3021, 302, '编辑风险点',   'F', 'T', NULL, NULL, 'risk:point:edit', NULL, 1, NOW(), NOW()),
(303,  3,   '风险巡检',     'C', 'T', '/safety/risk-check', 'safety/risk-check/index', 'risk:check',      'risk-check', 4, NOW(), NOW());

-- 套餐：有安全管理的旗舰版、标准版都加上
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',302,3021,303')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('302', menu_ids) = 0;

-- 角色：安全员管理风险点；作业负责人、监护人做自己负责的巡检
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (302, 3021, 303)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, 303
FROM sys_role r
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 303);

-- 演示化工的风险点（李四 103、赵六 104 是责任人；有的已逾期、有的今天到期，方便演示提醒）
INSERT INTO risk_point (id, tenant_id, point_code, point_name, point_type, location, dept_id, responsible_id, responsible_name, space_id, space_name,
                        hazard_factors, possible_accidents, lec_l, lec_e, lec_c, lec_d, risk_level, control_measures, check_cycle_days,
                        last_check_time, next_check_time, create_time, update_time) VALUES
(1, 1, 'RP-001', '甲醇储罐清罐作业', '作业活动', '罐区东侧', 1102, 103, '李四（作业负责人）', 1001, '1# 原料储罐 T-101',
    '罐内残留甲醇蒸气；通风不良导致缺氧；清罐时可能产生静电', '中毒窒息、火灾爆炸', 6, 3, 40, 720, 'MAJOR',
    '1. 进出物料管线加装盲板并挂牌\n2. 作业前 30 分钟内检测氧含量、可燃气体、有毒气体\n3. 连续强制通风，严禁通入纯氧\n4. 佩戴正压式空气呼吸器，系安全绳\n5. 出入口设专人监护，配备三脚架', 1,
    DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
(2, 1, 'RP-002', 'R-201 合成反应釜', '设备设施', '合成车间二楼', 1103, 103, '李四（作业负责人）', 1003, 'R-201 合成反应釜',
    '高温高压；介质为氢气、一氧化碳，泄漏后易燃易爆且有毒', '火灾爆炸、中毒', 3, 6, 15, 270, 'HIGH',
    '1. 压力、温度联锁投用正常\n2. 可燃、有毒气体报警器在检定有效期内\n3. 人孔、法兰无泄漏\n4. 安全阀铅封完好', 7,
    DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(CURDATE(), INTERVAL 17 HOUR), NOW(), NOW()),
(3, 1, 'RP-003', '污水处理调节池', '区域场所', '污水处理站', 1105, 104, '赵六（监护人）', 1004, '污水处理调节池',
    '池内可能积聚硫化氢、沼气；池边无防护可能坠落', '中毒窒息、淹溺', 3, 6, 15, 270, 'HIGH',
    '1. 池边护栏完好，警示标志清晰\n2. 硫化氢报警仪运行正常\n3. 进入池内必须办理受限空间作业票', 7,
    DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), NOW()),
(4, 1, 'RP-004', '罐区动火作业', '作业活动', '罐区东侧', 1102, 103, '李四（作业负责人）', NULL, NULL,
    '周边可能存在可燃物、可燃气体', '火灾爆炸', 1, 6, 15, 90, 'GENERAL',
    '1. 清除动火点周围 15 米内可燃物\n2. 动火前 30 分钟内可燃气体检测合格\n3. 配备不少于 2 具灭火器\n4. 设专人监火', 15,
    DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), NOW(), NOW()),
(5, 1, 'RP-005', '合成车间高处检修平台', '区域场所', '合成车间二楼', 1103, 104, '赵六（监护人）', NULL, NULL,
    '平台高度 6 米，护栏缺失或安全带未系挂可能坠落', '高处坠落、物体打击', 1, 6, 15, 90, 'GENERAL',
    '1. 平台护栏、踢脚板完好\n2. 作业人员系挂安全带，高挂低用\n3. 工具放入工具袋，下方设警戒区', 15,
    DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 18 HOUR), NOW(), NOW()),
(6, 1, 'RP-006', '北区地下电缆沟', '区域场所', '厂区北路', 1105, 104, '赵六（监护人）', 1005, '北区地下电缆沟',
    '沟内通风不良可能缺氧', '窒息', 0.5, 3, 15, 22.5, 'LOW',
    '1. 盖板完好，警示标志清晰\n2. 进入前进行氧含量检测', 30,
    DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY), NOW(), NOW()),
(7, 1, 'RP-007', '甲醇装卸车作业', '作业活动', '装卸站台', 1102, 103, '李四（作业负责人）', NULL, NULL,
    '装卸时甲醇泄漏、静电积聚', '火灾爆炸、中毒', 3, 6, 15, 270, 'HIGH',
    '1. 槽车静电接地夹可靠连接\n2. 装卸时司机离开驾驶室并熄火\n3. 现场配备防毒面具和应急冲洗设施', 7,
    DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 5 DAY), NOW(), NOW());

INSERT INTO risk_check (id, tenant_id, point_id, point_code, point_name, risk_level, checker_id, checker_name, check_time, result, items, remark, create_time, update_time) VALUES
(1, 1, 7, 'RP-007', '甲醇装卸车作业', 'HIGH', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 2 DAY), 'NORMAL',
    '[{"measure":"槽车静电接地夹可靠连接","ok":true,"note":null},{"measure":"装卸时司机离开驾驶室并熄火","ok":true,"note":null},{"measure":"现场配备防毒面具和应急冲洗设施","ok":true,"note":null}]', NULL, NOW(), NOW()),
(2, 1, 4, 'RP-004', '罐区动火作业', 'GENERAL', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 5 DAY), 'ABNORMAL',
    '[{"measure":"清除动火点周围 15 米内可燃物","ok":true,"note":null},{"measure":"动火前 30 分钟内可燃气体检测合格","ok":true,"note":null},{"measure":"配备不少于 2 具灭火器","ok":false,"note":"只有 1 具"},{"measure":"设专人监火","ok":true,"note":null}]',
    '已现场补充 1 具灭火器', NOW(), NOW());
