-- 第三十部分：安全投入台账（安全生产费用：按上年营业收入超额累退提取 → 分类预算 → 支出台账 → 使用统计）
-- 依据《企业安全生产费用提取和使用管理办法》（财资〔2022〕136 号）危险品生产与储存企业

CREATE TABLE cost_plan (
    id             BIGINT         NOT NULL COMMENT '主键',
    tenant_id      BIGINT         NOT NULL COMMENT '企业',
    year           INT            NOT NULL,
    revenue        DECIMAL(16, 2) NOT NULL COMMENT '上一年度营业收入（万元）',
    accrue_amount  DECIMAL(14, 2) NOT NULL COMMENT '本年应提（元），系统按超额累退计算',
    carry_over     DECIMAL(14, 2) NOT NULL DEFAULT 0 COMMENT '上年结转（元）',
    remark         VARCHAR(200)   NULL,
    updater_name   VARCHAR(50)    NULL,
    create_time    DATETIME       NULL,
    update_time    DATETIME       NULL,
    deleted        TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_year (tenant_id, year, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度安全生产费用计划';

CREATE TABLE cost_budget (
    id           BIGINT         NOT NULL COMMENT '主键',
    tenant_id    BIGINT         NOT NULL COMMENT '企业',
    plan_id      BIGINT         NOT NULL,
    year         INT            NOT NULL,
    category     VARCHAR(20)    NOT NULL COMMENT '使用类别，见 CostRules.CATEGORIES',
    amount       DECIMAL(14, 2) NOT NULL COMMENT '预算（元）',
    create_time  DATETIME       NULL,
    update_time  DATETIME       NULL,
    deleted      TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全生产费用分类预算';

CREATE TABLE cost_expense (
    id             BIGINT         NOT NULL COMMENT '主键',
    tenant_id      BIGINT         NOT NULL COMMENT '企业',
    expense_no     VARCHAR(20)    NOT NULL,
    year           INT            NOT NULL COMMENT '支出日期所在年度',
    category       VARCHAR(20)    NOT NULL,
    title          VARCHAR(100)   NOT NULL COMMENT '支出事项',
    amount         DECIMAL(14, 2) NOT NULL COMMENT '金额（元）',
    expense_date   DATE           NOT NULL,
    dept_name      VARCHAR(50)    NULL COMMENT '使用部门',
    payee          VARCHAR(100)   NULL COMMENT '收款单位',
    voucher_no     VARCHAR(50)    NULL COMMENT '凭证号 / 发票号',
    hazard_id      BIGINT         NULL COMMENT '关联隐患（隐患整改资金）',
    hazard_no      VARCHAR(32)    NULL,
    hazard_title   VARCHAR(100)   NULL,
    recorder_id    BIGINT         NOT NULL,
    recorder_name  VARCHAR(50)    NOT NULL,
    status         TINYINT        NOT NULL DEFAULT 1 COMMENT '1 有效 / 0 作废',
    void_reason    VARCHAR(100)   NULL,
    voider_name    VARCHAR(50)    NULL,
    void_time      DATETIME       NULL,
    remark         VARCHAR(200)   NULL,
    create_time    DATETIME       NULL,
    update_time    DATETIME       NULL,
    deleted        TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_year (tenant_id, year, category),
    KEY idx_hazard (hazard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全生产费用支出台账';

-- 菜单：安全管理下面加【安全投入】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(318,  3,   '安全投入', 'C', 'T', '/safety/cost', 'safety/cost/index', 'cost:list', 'cost', 0, NOW(), NOW()),
(3181, 318, '编制计划、登记支出', 'F', 'T', NULL, NULL, 'cost:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',318,3181')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('318', menu_ids) = 0;

-- 安全员查看和登记；企业管理员本来就有全部权限
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (318, 3181)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ===== 演示数据 =====
-- 去年：营业收入 7200 万 → 应提 45 + 6200 × 2.25% = 184.5 万；结转 12 万；用了 177.86 万，余 18.64 万结转到今年
-- 今年：营业收入 8000 万 → 应提 45 + 7000 × 2.25% = 202.5 万；可用 202.5 + 18.64 = 221.14 万
INSERT INTO cost_plan (id, tenant_id, year, revenue, accrue_amount, carry_over, remark, updater_name, create_time, update_time) VALUES
(9801, 1, YEAR(CURDATE()) - 1, 7200, 1845000, 120000, NULL, '张三（安全员）', NOW(), NOW()),
(9802, 1, YEAR(CURDATE()), 8000, 2025000, 186400, '按 2026 年度安全投入计划会审定', '张三（安全员）', NOW(), NOW());

INSERT INTO cost_budget (id, tenant_id, plan_id, year, category, amount, create_time, update_time) VALUES
(9801, 1, 9801, YEAR(CURDATE()) - 1, 'FACILITY',    650000, NOW(), NOW()),
(9802, 1, 9801, YEAR(CURDATE()) - 1, 'RISK_HAZARD', 450000, NOW(), NOW()),
(9803, 1, 9801, YEAR(CURDATE()) - 1, 'EMERGENCY',   250000, NOW(), NOW()),
(9804, 1, 9801, YEAR(CURDATE()) - 1, 'PPE',         200000, NOW(), NOW()),
(9805, 1, 9801, YEAR(CURDATE()) - 1, 'TESTING',     170000, NOW(), NOW()),
(9806, 1, 9801, YEAR(CURDATE()) - 1, 'INSURANCE',   150000, NOW(), NOW()),
(9811, 1, 9802, YEAR(CURDATE()), 'FACILITY',    650000, NOW(), NOW()),
(9812, 1, 9802, YEAR(CURDATE()), 'EMERGENCY',   180000, NOW(), NOW()),
(9813, 1, 9802, YEAR(CURDATE()), 'RISK_HAZARD', 420000, NOW(), NOW()),
(9814, 1, 9802, YEAR(CURDATE()), 'INSPECTION',  150000, NOW(), NOW()),
(9815, 1, 9802, YEAR(CURDATE()), 'PPE',         120000, NOW(), NOW()),
(9816, 1, 9802, YEAR(CURDATE()), 'TRAINING',     90000, NOW(), NOW()),
(9817, 1, 9802, YEAR(CURDATE()), 'NEW_TECH',    200000, NOW(), NOW()),
(9818, 1, 9802, YEAR(CURDATE()), 'TESTING',     110000, NOW(), NOW()),
(9819, 1, 9802, YEAR(CURDATE()), 'INSURANCE',   160000, NOW(), NOW()),
(9820, 1, 9802, YEAR(CURDATE()), 'OTHER',        30000, NOW(), NOW());

-- 支出日期：今年的按“一年中的第几天”，不会晚于今天；编号和年度最后统一算
INSERT INTO cost_expense (id, tenant_id, expense_no, year, category, title, amount, expense_date, dept_name, payee, voucher_no, hazard_id, hazard_no, hazard_title, recorder_id, recorder_name, status, void_reason, voider_name, void_time, remark, create_time, update_time) VALUES
(9801, 1, '-', 0, 'FACILITY',    '罐区紧急切断阀（SIS）改造',        620000, MAKEDATE(YEAR(CURDATE()) - 1, 95),  '罐区',     '演示自控工程有限公司', '记-2025-0412', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9802, 1, '-', 0, 'RISK_HAZARD', '双重预防信息化平台建设',            410000, MAKEDATE(YEAR(CURDATE()) - 1, 160), '安全环保部', '演示软件有限公司',     '记-2025-0633', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9803, 1, '-', 0, 'EMERGENCY',   '消防车辆维保与应急器材更新',        235000, MAKEDATE(YEAR(CURDATE()) - 1, 200), '安全环保部', '演示消防器材有限公司', '记-2025-0801', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9804, 1, '-', 0, 'PPE',         '年度劳保用品采购',                  198600, MAKEDATE(YEAR(CURDATE()) - 1, 60),  '生产部',   '演示劳保用品有限公司', '记-2025-0218', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9805, 1, '-', 0, 'TESTING',     '特种设备年度检验',                  165000, MAKEDATE(YEAR(CURDATE()) - 1, 250), '设备部',   '演示特种设备检验所',   '记-2025-0915', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9806, 1, '-', 0, 'INSURANCE',   '安全生产责任保险（全年）',          150000, MAKEDATE(YEAR(CURDATE()) - 1, 15),  '安全环保部', '演示财产保险有限公司', '记-2025-0107', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9811, 1, '-', 0, 'INSURANCE',   '安全生产责任保险（全年）',          158000, LEAST(MAKEDATE(YEAR(CURDATE()), 12),  CURDATE()), '安全环保部', '演示财产保险有限公司', '记-0109', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9812, 1, '-', 0, 'PPE',         '冬季防寒劳保用品及防化服采购',      46800,  LEAST(MAKEDATE(YEAR(CURDATE()), 25),  CURDATE()), '生产部',   '演示劳保用品有限公司', '记-0121', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9813, 1, '-', 0, 'TRAINING',    '节后复工全员安全培训',              12600,  LEAST(MAKEDATE(YEAR(CURDATE()), 40),  CURDATE()), '安全环保部', '演示安全培训中心',     '记-0207', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9814, 1, '-', 0, 'FACILITY',    '罐区可燃气体检测报警器更换（12 台）', 86400, LEAST(MAKEDATE(YEAR(CURDATE()), 58),  CURDATE()), '罐区',     '演示仪表有限公司',     '记-0226', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9815, 1, '-', 0, 'TESTING',     '压力容器、安全阀年度检验',          38500,  LEAST(MAKEDATE(YEAR(CURDATE()), 75),  CURDATE()), '设备部',   '演示特种设备检验所',   '记-0315', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9816, 1, '-', 0, 'EMERGENCY',   '应急物资补充（正压式空气呼吸器 6 套）', 54000, LEAST(MAKEDATE(YEAR(CURDATE()), 96), CURDATE()), '安全环保部', '演示消防器材有限公司', '记-0406', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9817, 1, '-', 0, 'RISK_HAZARD', '双重预防信息化平台年度运维',        68000,  LEAST(MAKEDATE(YEAR(CURDATE()), 120), CURDATE()), '安全环保部', '演示软件有限公司',     '记-0430', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9818, 1, '-', 0, 'PPE',         '防静电工作服、安全鞋发放',          52300,  LEAST(MAKEDATE(YEAR(CURDATE()), 135), CURDATE()), '生产部',   '演示劳保用品有限公司', '记-0515', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9819, 1, '-', 0, 'INSPECTION',  '安全生产标准化复评咨询',            45000,  LEAST(MAKEDATE(YEAR(CURDATE()), 150), CURDATE()), '安全环保部', '演示安全技术咨询有限公司', '记-0530', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9820, 1, '-', 0, 'OTHER',       '办公室空调采购',                    9800,   LEAST(MAKEDATE(YEAR(CURDATE()), 160), CURDATE()), '综合办',   '演示电器商行',         '记-0609', NULL, NULL, NULL, 102, '张三（安全员）', 0, '不属于安全生产费用使用范围，改由管理费用列支', '演示企业管理员', NOW(), NULL, NOW(), NOW()),
(9821, 1, '-', 0, 'FACILITY',    '合成车间防爆风机改造',              132000, LEAST(MAKEDATE(YEAR(CURDATE()), 170), CURDATE()), '合成车间', '演示机电设备有限公司', '记-0619', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9822, 1, '-', 0, 'EMERGENCY',   '夏季综合应急演练（泄漏 + 火灾）',   18600,  LEAST(MAKEDATE(YEAR(CURDATE()), 190), CURDATE()), '安全环保部', NULL,                  '记-0709', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9823, 1, '-', 0, 'TESTING',     '起重机械、叉车定期检验',            21800,  LEAST(MAKEDATE(YEAR(CURDATE()), 205), CURDATE()), '设备部',   '演示特种设备检验所',   '记-0724', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9824, 1, '-', 0, 'RISK_HAZARD', 'R-201 反应釜人孔垫片更换（隐患整改）', 8900, LEAST(MAKEDATE(YEAR(CURDATE()), 220), CURDATE()), '合成车间', '演示密封件有限公司',   '记-0808', 2, 'YH20261020-0002', 'R-201 反应釜人孔垫片老化渗漏', 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9825, 1, '-', 0, 'NEW_TECH',    '罐区视频 AI 行为识别试点',          96000,  LEAST(MAKEDATE(YEAR(CURDATE()), 240), CURDATE()), '罐区',     '演示智能科技有限公司', '记-0828', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9826, 1, '-', 0, 'PPE',         '防毒面具滤毒罐批量更换',            32700,  LEAST(MAKEDATE(YEAR(CURDATE()), 255), CURDATE()), '生产部',   '演示劳保用品有限公司', '记-0912', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, '滤毒罐到期集中更换，超出年初预算', NOW(), NOW()),
(9827, 1, '-', 0, 'TRAINING',    '特种作业人员取证复审培训',          23400,  LEAST(MAKEDATE(YEAR(CURDATE()), 270), CURDATE()), '安全环保部', '演示安全培训中心',     '记-0927', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9828, 1, '-', 0, 'TRAINING',    '隐患随手拍奖励（第三季度）',        3200,   LEAST(MAKEDATE(YEAR(CURDATE()), 280), CURDATE()), '安全环保部', NULL,                  '记-1007', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, '员工发现并报告事故隐患的奖励', NOW(), NOW()),
(9829, 1, '-', 0, 'FACILITY',    '罐区围堰防渗修复',                  74500,  LEAST(MAKEDATE(YEAR(CURDATE()), 285), CURDATE()), '罐区',     '演示防腐工程有限公司', '记-1012', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9830, 1, '-', 0, 'RISK_HAZARD', '重大危险源在线监测系统接入',        56000,  LEAST(MAKEDATE(YEAR(CURDATE()), 300), CURDATE()), '安全环保部', '演示软件有限公司',     '记-1027', NULL, NULL, NULL, 102, '张三（安全员）', 1, NULL, NULL, NULL, NULL, NOW(), NOW());

UPDATE cost_expense SET year = YEAR(expense_date),
    expense_no = CONCAT('AF', DATE_FORMAT(expense_date, '%Y%m%d'), '-', LPAD(id - 9800, 4, '0'))
WHERE tenant_id = 1 AND id BETWEEN 9801 AND 9830;
