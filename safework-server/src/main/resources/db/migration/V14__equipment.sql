-- 第十九部分：设备设施（特种设备、安全附件、检测报警仪器、消防设施的台账和定期检验）

CREATE TABLE equipment (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '企业',
    equip_code        VARCHAR(30)   NOT NULL COMMENT '设备位号',
    name              VARCHAR(60)   NOT NULL,
    category          VARCHAR(12)   NOT NULL COMMENT 'SPECIAL / ACCESSORY / DETECTOR / FIRE / OTHER',
    equip_type        VARCHAR(30)   NOT NULL COMMENT '设备类型，决定默认检验周期',
    location          VARCHAR(100)  NOT NULL,
    responsible_id    BIGINT        NULL,
    responsible_name  VARCHAR(50)   NULL,
    risk_point_id     BIGINT        NULL COMMENT '关联风险点',
    risk_point_name   VARCHAR(80)   NULL,
    register_no       VARCHAR(50)   NULL COMMENT '特种设备使用登记证编号',
    manufacturer      VARCHAR(60)   NULL,
    model             VARCHAR(60)   NULL,
    install_date      DATE          NULL,
    cycle_months      INT           NOT NULL COMMENT '检验周期（月）',
    last_inspect_date DATE          NULL,
    next_inspect_date DATE          NULL,
    last_result       VARCHAR(8)    NULL COMMENT 'PASS / FAIL',
    status            VARCHAR(10)   NOT NULL COMMENT 'IN_USE / STOPPED / SCRAPPED',
    status_reason     VARCHAR(200)  NULL,
    remark            VARCHAR(200)  NULL,
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, equip_code, deleted),
    KEY idx_tenant_next (tenant_id, status, next_inspect_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备设施';

CREATE TABLE equipment_inspection (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '企业',
    equipment_id      BIGINT        NOT NULL,
    inspect_date      DATE          NOT NULL,
    result            VARCHAR(8)    NOT NULL COMMENT 'PASS / FAIL',
    org               VARCHAR(60)   NULL COMMENT '检验机构',
    report_no         VARCHAR(50)   NULL,
    conclusion        VARCHAR(500)  NULL,
    next_inspect_date DATE          NULL,
    hazard_id         BIGINT        NULL COMMENT '不合格转的隐患',
    hazard_no         VARCHAR(30)   NULL,
    recorder_name     VARCHAR(50)   NULL,
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_equipment (tenant_id, equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备检验记录';

-- 菜单：安全管理下面加【设备设施】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(308,  3,   '设备设施', 'C', 'T', '/safety/equipment', 'safety/equipment/index', 'equipment:list', 'equipment', 9, NOW(), NOW()),
(3081, 308, '管理设备台账', 'F', 'T', NULL, NULL, 'equipment:edit', NULL, 1, NOW(), NOW()),
(3082, 308, '登记检验结果', 'F', 'T', NULL, NULL, 'equipment:inspect', NULL, 2, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',308,3081,3082')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('308', menu_ids) = 0;

-- 安全员全部；作业负责人、监护人可以查看（作业前看看设备、报警器是否在检验有效期内）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (308, 3081, 3082)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 308
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示设备：正常、30 天内到期、逾期、从未检验、检验不合格停用，各种情况都有
INSERT INTO equipment (id, tenant_id, equip_code, name, category, equip_type, location, responsible_id, responsible_name, risk_point_id, risk_point_name,
                       register_no, manufacturer, model, install_date, cycle_months, last_inspect_date, next_inspect_date, last_result, status, status_reason, remark, create_time, update_time) VALUES
(9901, 1, 'R-201', '合成反应釜', 'SPECIAL', 'PRESSURE_VESSEL', '合成车间二楼', 103, '李四（作业负责人）', 2, 'RP-002 R-201 合成反应釜',
 '容3PA皖A01234(21)', '合肥通用机械有限公司', 'FY-5000', '2021-05-10', 36, DATE_SUB(CURDATE(), INTERVAL 32 MONTH), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 32 MONTH), INTERVAL 36 MONTH), 'PASS', 'IN_USE', NULL, '设计压力 1.6MPa', NOW(), NOW()),
(9902, 1, 'SV-201', 'R-201 安全阀', 'ACCESSORY', 'SAFETY_VALVE', '合成车间二楼 R-201 顶部', 103, '李四（作业负责人）', 2, 'RP-002 R-201 合成反应釜',
 NULL, '浙江永盛阀门', 'A42Y-16C DN50', '2021-05-10', 12, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 12 MONTH), INTERVAL 20 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'PASS', 'IN_USE', NULL, '整定压力 1.5MPa', NOW(), NOW()),
(9903, 1, 'PG-201', 'R-201 压力表', 'ACCESSORY', 'PRESSURE_GAUGE', '合成车间二楼 R-201 顶部', 103, '李四（作业负责人）', 2, 'RP-002 R-201 合成反应釜',
 NULL, '上海仪表厂', 'Y-100 0~2.5MPa', '2021-05-10', 6, DATE_SUB(CURDATE(), INTERVAL 7 MONTH), DATE_SUB(CURDATE(), INTERVAL 1 MONTH), 'PASS', 'IN_USE', NULL, NULL, NOW(), NOW()),
(9904, 1, 'GD-101', '罐区可燃气体报警器', 'DETECTOR', 'COMBUSTIBLE_DETECTOR', '罐区东侧', 102, '张三（安全员）', 1, 'RP-001 甲醇储罐清罐作业',
 NULL, '汉威科技', 'BS03II', '2022-03-01', 12, DATE_SUB(CURDATE(), INTERVAL 3 MONTH), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 3 MONTH), INTERVAL 12 MONTH), 'PASS', 'IN_USE', NULL, NULL, NOW(), NOW()),
(9905, 1, 'GD-301', '污水站硫化氢报警器', 'DETECTOR', 'TOXIC_DETECTOR', '污水处理站调节池', 102, '张三（安全员）', 3, 'RP-003 污水处理调节池',
 NULL, '汉威科技', 'BS03II-H2S', '2022-03-01', 12, DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 YEAR), INTERVAL 12 MONTH), 'FAIL', 'STOPPED',
 '检验不合格：传感器响应时间 95 秒，超过 60 秒要求', NULL, NOW(), NOW()),
(9906, 1, 'QZ-01', '5t 电动单梁起重机', 'SPECIAL', 'CRANE', '维修车间', 103, '李四（作业负责人）', NULL, NULL,
 '起17皖A00567(22)', '河南卫华', 'LD5-16.5', '2022-08-15', 24, DATE_SUB(CURDATE(), INTERVAL 20 MONTH), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 20 MONTH), INTERVAL 24 MONTH), 'PASS', 'IN_USE', NULL, NULL, NOW(), NOW()),
(9907, 1, 'CC-02', '3t 内燃叉车', 'SPECIAL', 'FORKLIFT', '成品仓库', 103, '李四（作业负责人）', NULL, NULL,
 '车11皖A08899(26)', '合力叉车', 'CPCD30', DATE_SUB(CURDATE(), INTERVAL 20 DAY), 12, NULL, NULL, NULL, 'IN_USE', NULL, '新购置，待首次检验', NOW(), NOW()),
(9908, 1, 'MH-A', '罐区干粉灭火器（20 具）', 'FIRE', 'EXTINGUISHER', '罐区', 104, '赵六（监护人）', NULL, NULL,
 NULL, '浙江消防器材', 'MFZ/ABC8', '2023-06-01', 12, DATE_SUB(CURDATE(), INTERVAL 6 MONTH), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 6 MONTH), INTERVAL 12 MONTH), 'PASS', 'IN_USE', NULL, NULL, NOW(), NOW()),
(9909, 1, 'KH-01', '正压式空气呼吸器', 'FIRE', 'BREATHING_APPARATUS', '应急物资柜（中控室）', 104, '赵六（监护人）', 1, 'RP-001 甲醇储罐清罐作业',
 NULL, '霍尼韦尔', 'C900', '2023-01-10', 12, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 12 MONTH), INTERVAL 7 DAY), DATE_ADD(CURDATE(), INTERVAL 7 DAY), 'PASS', 'IN_USE', NULL, '受限空间救援用', NOW(), NOW()),
(9910, 1, 'PD-01', '便携式四合一气体检测仪', 'DETECTOR', 'PORTABLE_DETECTOR', '安全环保部', 102, '张三（安全员）', NULL, NULL,
 NULL, '梅思安', 'ALTAIR 4XR', '2024-02-20', 12, DATE_SUB(CURDATE(), INTERVAL 2 MONTH), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), INTERVAL 12 MONTH), 'PASS', 'IN_USE', NULL, '受限空间作业前检测用', NOW(), NOW()),
(9951, 2, 'GL-01', '转炉余热锅炉', 'SPECIAL', 'BOILER', '炼钢车间', 201, '测试企业管理员', NULL, NULL,
 '锅11皖E00123(20)', '杭州锅炉', 'QC-35', '2020-04-01', 12, DATE_SUB(CURDATE(), INTERVAL 4 MONTH), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 4 MONTH), INTERVAL 12 MONTH), 'PASS', 'IN_USE', NULL, NULL, NOW(), NOW());

INSERT INTO equipment_inspection (id, tenant_id, equipment_id, inspect_date, result, org, report_no, conclusion, next_inspect_date, recorder_name, create_time, update_time) VALUES
(9921, 1, 9901, DATE_SUB(CURDATE(), INTERVAL 32 MONTH), 'PASS', '安徽省特种设备检测院', 'RQD-2024-0518', '符合要求，安全状况等级 2 级', DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 32 MONTH), INTERVAL 36 MONTH), '张三（安全员）', NOW(), NOW()),
(9922, 1, 9902, DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 12 MONTH), INTERVAL 20 DAY), 'PASS', '合肥市特种设备检验所', 'AQF-2025-1127', '整定压力 1.5MPa，密封试验合格', DATE_ADD(CURDATE(), INTERVAL 20 DAY), '张三（安全员）', NOW(), NOW()),
(9923, 1, 9903, DATE_SUB(CURDATE(), INTERVAL 7 MONTH), 'PASS', '合肥市计量测试研究院', 'JD-2026-0322', '检定合格', DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '张三（安全员）', NOW(), NOW()),
(9924, 1, 9905, DATE_SUB(CURDATE(), INTERVAL 1 YEAR), 'PASS', '合肥市计量测试研究院', 'JD-2025-1030', '检定合格', DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 1 YEAR), INTERVAL 12 MONTH), '张三（安全员）', NOW(), NOW()),
(9925, 1, 9905, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'FAIL', '合肥市计量测试研究院', 'JD-2026-1026', '传感器响应时间 95 秒，超过 60 秒要求，需更换传感器后复检', NULL, '张三（安全员）', NOW(), NOW());
