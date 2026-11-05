-- 第二十三部分：职业健康（危害因素 → 岗位接害（岗位复用劳保模块）→ 体检记录 → 监护台账 → 异常处理）

CREATE TABLE oh_factor (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '企业',
    name              VARCHAR(40)   NOT NULL COMMENT '危害因素名称',
    category          VARCHAR(12)   NOT NULL COMMENT 'CHEMICAL / DUST / PHYSICAL / BIOLOGICAL / RADIATION',
    cycle_months      INT           NOT NULL DEFAULT 12 COMMENT '在岗体检周期（月）',
    limit_value       VARCHAR(60)   NULL COMMENT '职业接触限值',
    exam_items        VARCHAR(300)  NULL COMMENT '体检重点项目',
    contraindication  VARCHAR(300)  NULL COMMENT '职业禁忌证',
    status            TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    remark            VARCHAR(200)  NULL,
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职业病危害因素';

CREATE TABLE oh_post_factor (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    post_id       BIGINT        NOT NULL COMMENT '岗位（ppe_post）',
    factor_id     BIGINT        NOT NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_post (tenant_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位接触的危害因素';

CREATE TABLE oh_exam (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    user_id         BIGINT        NOT NULL,
    user_name       VARCHAR(50)   NOT NULL,
    exam_type       VARCHAR(10)   NOT NULL COMMENT 'PRE 上岗前 / ON 在岗 / LEAVE 离岗 / EMERGENCY 应急',
    exam_date       DATE          NOT NULL,
    org             VARCHAR(60)   NULL COMMENT '体检机构',
    report_no       VARCHAR(40)   NULL,
    conclusion      VARCHAR(20)   NOT NULL COMMENT 'NORMAL / OTHER / RECHECK / SUSPECT / CONTRAINDICATION',
    factors         VARCHAR(300)  NULL COMMENT '体检时接触的危害因素（快照）',
    detail          VARCHAR(500)  NULL,
    recheck_date    DATE          NULL,
    handle_status   VARCHAR(8)    NOT NULL DEFAULT 'NONE' COMMENT 'NONE 不需要处理 / OPEN 待处理 / CLOSED 已处理',
    handle_measure  VARCHAR(300)  NULL,
    handler_name    VARCHAR(50)   NULL,
    handle_time     DATETIME      NULL,
    recorder_name   VARCHAR(50)   NULL,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_user (tenant_id, user_id, exam_date),
    KEY idx_tenant_handle (tenant_id, handle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='职业健康检查记录';

-- 菜单：安全管理下面加【职业健康】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(311,  3,   '职业健康', 'C', 'T', '/safety/health', 'safety/health/index', 'health:list', 'health', 12, NOW(), NOW()),
(3111, 311, '管理体检和危害因素', 'F', 'T', NULL, NULL, 'health:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',311,3111')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('311', menu_ids) = 0;

-- 安全员管理；作业负责人、监护人看自己的
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (311, 3111)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 311
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示危害因素
INSERT INTO oh_factor (id, tenant_id, name, category, cycle_months, limit_value, exam_items, contraindication, status, remark, create_time, update_time) VALUES
(9801, 1, '甲醇', 'CHEMICAL', 12, 'PC-TWA 25 mg/m³，PC-STEL 50 mg/m³', '神经系统检查、眼底检查', '视网膜及视神经病', 1, '罐区、装卸', NOW(), NOW()),
(9802, 1, '硫化氢', 'CHEMICAL', 12, 'MAC 10 mg/m³', '神经系统检查、胸部 X 线、心电图', '中枢神经系统器质性疾病', 1, '污水处理、受限空间', NOW(), NOW()),
(9803, 1, '噪声', 'PHYSICAL', 12, '8h 等效声级 85 dB(A)', '纯音听阈测试', '各种原因引起永久性感音神经性听力损失', 1, NULL, NOW(), NOW()),
(9804, 1, '苯', 'CHEMICAL', 12, 'PC-TWA 6 mg/m³', '血常规（白细胞、血小板）', '血常规检出异常、造血系统疾病', 1, NULL, NOW(), NOW()),
(9805, 1, '矽尘', 'DUST', 12, 'PC-TWA 1 mg/m³（总尘）', '胸部 X 线、肺功能', '活动性肺结核、慢性阻塞性肺病', 1, NULL, NOW(), NOW()),
(9806, 1, '高温', 'PHYSICAL', 12, 'WBGT 指数按劳动强度', '血压、心电图（每年入暑前）', '未控制的高血压、糖尿病', 1, NULL, NOW(), NOW());

-- 罐区操作工接触甲醇、硫化氢、噪声（岗位来自劳保模块 V17）
INSERT INTO oh_post_factor (id, tenant_id, post_id, factor_id, create_time, update_time) VALUES
(9811, 1, 9711, 9801, NOW(), NOW()),
(9812, 1, 9711, 9802, NOW(), NOW()),
(9813, 1, 9711, 9803, NOW(), NOW());

-- 演示体检：李四在岗体检已逾期；赵六最近一次需要复查（听力），还没处理
INSERT INTO oh_exam (id, tenant_id, user_id, user_name, exam_type, exam_date, org, report_no, conclusion, factors, detail, recheck_date,
                     handle_status, handle_measure, handler_name, handle_time, recorder_name, create_time, update_time) VALUES
(9821, 1, 103, '李四（作业负责人）', 'PRE', DATE_SUB(CURDATE(), INTERVAL 800 DAY), '合肥市职业病防治院', 'ZJ-2024-0611', 'NORMAL', '甲醇、硫化氢、噪声', NULL, NULL,
 'NONE', NULL, NULL, NULL, '张三（安全员）', NOW(), NOW()),
(9822, 1, 103, '李四（作业负责人）', 'ON', DATE_SUB(CURDATE(), INTERVAL 400 DAY), '合肥市职业病防治院', 'ZJ-2025-0927', 'NORMAL', '甲醇、硫化氢、噪声', NULL, NULL,
 'NONE', NULL, NULL, NULL, '张三（安全员）', NOW(), NOW()),
(9823, 1, 104, '赵六（监护人）', 'PRE', DATE_SUB(CURDATE(), INTERVAL 700 DAY), '合肥市职业病防治院', 'ZJ-2024-1002', 'NORMAL', '甲醇、硫化氢、噪声', NULL, NULL,
 'NONE', NULL, NULL, NULL, '张三（安全员）', NOW(), NOW()),
(9824, 1, 104, '赵六（监护人）', 'ON', DATE_SUB(CURDATE(), INTERVAL 340 DAY), '合肥市职业病防治院', 'ZJ-2025-1201', 'RECHECK', '甲醇、硫化氢、噪声',
 '双耳高频听阈 4000Hz 升高至 35dB，建议脱离噪声 48 小时后复查', DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 340 DAY), INTERVAL 30 DAY),
 'OPEN', NULL, NULL, NULL, '张三（安全员）', NOW(), NOW());
