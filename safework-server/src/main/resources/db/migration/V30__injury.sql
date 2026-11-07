-- 第三十五部分：工伤管理（《工伤保险条例》：受伤 30 日内申请工伤认定 → 认定结果 → 劳动能力鉴定 → 停工留薪期 → 费用台账 → 结案）

CREATE TABLE injury_case (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    case_no         VARCHAR(20)   NOT NULL,
    injured_name    VARCHAR(30)   NOT NULL,
    injured_user_id BIGINT        NULL COMMENT '本企业员工时关联账号',
    employer_type   VARCHAR(10)   NOT NULL COMMENT 'OWN 本企业 / CONTRACTOR 承包商',
    employer_name   VARCHAR(60)   NOT NULL,
    dept_name       VARCHAR(50)   NULL,
    injury_time     DATETIME      NOT NULL,
    location        VARCHAR(60)   NOT NULL,
    description     VARCHAR(500)  NOT NULL,
    injury_part     VARCHAR(30)   NULL COMMENT '受伤部位',
    injury_nature   VARCHAR(30)   NULL COMMENT '伤害性质',
    event_id        BIGINT        NULL COMMENT '关联事故快报',
    event_no        VARCHAR(30)   NULL,
    status          VARCHAR(16)   NOT NULL COMMENT 'REPORTED / APPLIED / RECOGNIZED / NOT_RECOGNIZED / APPRAISED / CLOSED',
    apply_date      DATE          NULL COMMENT '申请工伤认定日期',
    apply_no        VARCHAR(40)   NULL,
    decision_date   DATE          NULL,
    decision_no     VARCHAR(40)   NULL COMMENT '认定决定书编号',
    decision_note   VARCHAR(200)  NULL,
    grade           INT           NULL COMMENT '伤残等级 1~10，0 = 未达到等级',
    appraise_date   DATE          NULL,
    monthly_wage    DECIMAL(10,2) NULL COMMENT '本人工资（月）',
    leave_start     DATE          NULL COMMENT '停工留薪期',
    leave_end       DATE          NULL,
    leave_extended  TINYINT       NOT NULL DEFAULT 0,
    return_date     DATE          NULL COMMENT '复工日期',
    close_date      DATE          NULL,
    close_note      VARCHAR(200)  NULL,
    recorder_name   VARCHAR(50)   NOT NULL,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_tenant_time (tenant_id, injury_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工伤案件';

CREATE TABLE injury_cost (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    case_id       BIGINT        NOT NULL,
    category      VARCHAR(16)   NOT NULL COMMENT 'MEDICAL / FOOD / TRAVEL / REHAB / NURSING / LEAVE_WAGE / LUMP_SUM / OTHER',
    amount        DECIMAL(12,2) NOT NULL COMMENT '元',
    payer         VARCHAR(10)   NOT NULL COMMENT 'FUND 工伤保险基金 / COMPANY 单位',
    pay_date      DATE          NOT NULL,
    remark        VARCHAR(100)  NULL,
    recorder_name VARCHAR(50)   NOT NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_case (tenant_id, case_id),
    KEY idx_tenant_date (tenant_id, pay_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工伤费用';

-- 菜单：安全管理下面加【工伤管理】；涉及个人信息，只给安全员
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(322,  3,   '工伤管理', 'C', 'T', '/safety/injury', 'safety/injury/index', 'injury:list', 'injury', 19, NOW(), NOW()),
(3221, 322, '登记和办理', 'F', 'T', NULL, NULL, 'injury:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',322,3221')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('322', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (322, 3221)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ===== 演示数据（日期相对今天）=====
-- 1. 承包商人员硫化氢中毒（关联事故快报 9402，20 天前）：承包商单位已申请认定，等结果
-- 2. 李四手部割伤（26 天前）：还没申请认定，还剩 4 天
-- 3. 赵六高处坠落（约 8 个月前）：认定工伤、十级伤残、停工留薪 3 个月，已结案
INSERT INTO injury_case (id, tenant_id, case_no, injured_name, injured_user_id, employer_type, employer_name, dept_name, injury_time, location, description,
                         injury_part, injury_nature, event_id, event_no, status, apply_date, apply_no, decision_date, decision_no, decision_note,
                         grade, appraise_date, monthly_wage, leave_start, leave_end, leave_extended, return_date, close_date, close_note, recorder_name, create_time, update_time)
SELECT 9961, 1, CONCAT('GS', DATE_FORMAT(DATE(e.occur_time), '%Y%m%d'), '-9961'), '刘强', NULL, 'CONTRACTOR', '合肥安达建设工程有限公司', NULL, e.occur_time, e.location,
       '进入 1# 调节池清淤约 10 分钟后头晕、恶心，被监护人拉出池外送医，诊断为轻度硫化氢中毒，住院观察 2 天后出院。',
       '呼吸系统', '急性中毒', e.id, e.event_no, 'APPLIED', DATE_SUB(CURDATE(), INTERVAL 12 DAY), '合人社工伤受字〔演示〕0318号', NULL, NULL, NULL,
       NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, '张三（安全员）', e.occur_time, NOW()
FROM emergency_event e WHERE e.id = 9402
  AND NOT EXISTS (SELECT 1 FROM injury_case c WHERE c.id = 9961);

INSERT INTO injury_case (id, tenant_id, case_no, injured_name, injured_user_id, employer_type, employer_name, dept_name, injury_time, location, description,
                         injury_part, injury_nature, status, leave_extended, recorder_name, create_time, update_time)
SELECT 9962, 1, CONCAT('GS', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 26 DAY), '%Y%m%d'), '-9962'), '李四（作业负责人）', 103, 'OWN', '本企业', '储运车间',
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 26 DAY), '10:15:00'), '储运车间 2# 泵房',
       '更换 P-202 泵机械密封时扳手打滑，左手被泵壳边缘割伤，现场包扎后送医缝合 6 针。',
       '左手', '割伤', 'REPORTED', 0, '张三（安全员）', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 26 DAY), '11:00:00'), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM injury_case c WHERE c.id = 9962);

INSERT INTO injury_case (id, tenant_id, case_no, injured_name, injured_user_id, employer_type, employer_name, dept_name, injury_time, location, description,
                         injury_part, injury_nature, status, apply_date, apply_no, decision_date, decision_no, decision_note,
                         grade, appraise_date, monthly_wage, leave_start, leave_end, leave_extended, return_date, close_date, close_note, recorder_name, create_time, update_time)
SELECT 9963, 1, CONCAT('GS', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 240 DAY), '%Y%m%d'), '-9963'), '赵六（监护人）', 104, 'OWN', '本企业', '储运车间',
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 240 DAY), '15:40:00'), '罐区 3# 储罐爬梯',
       '巡检下爬梯时踩空，从约 1.5 米高处坠落，右脚着地，诊断为右踝关节骨折。',
       '右踝', '骨折', 'CLOSED', DATE_SUB(CURDATE(), INTERVAL 230 DAY), '合人社工伤受字〔演示〕0102号',
       DATE_SUB(CURDATE(), INTERVAL 190 DAY), '合人社工伤认字〔演示〕0087号', '认定为工伤',
       10, DATE_SUB(CURDATE(), INTERVAL 120 DAY), 4200.00, DATE_SUB(CURDATE(), INTERVAL 240 DAY), DATE_SUB(CURDATE(), INTERVAL 150 DAY), 0,
       DATE_SUB(CURDATE(), INTERVAL 150 DAY), DATE_SUB(CURDATE(), INTERVAL 90 DAY), '十级伤残，一次性伤残补助金已由工伤保险基金支付，本人已复工', '张三（安全员）',
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 240 DAY), '17:00:00'), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM injury_case c WHERE c.id = 9963);

INSERT INTO injury_cost (id, tenant_id, case_id, category, amount, payer, pay_date, remark, recorder_name, create_time, update_time)
SELECT v.id, 1, v.case_id, v.category, v.amount, v.payer, v.pay_date, v.remark, '张三（安全员）', NOW(), NOW()
FROM (
    SELECT 99611 AS id, 9961 AS case_id, 'MEDICAL' AS category, 3860.00 AS amount, 'COMPANY' AS payer, DATE_SUB(CURDATE(), INTERVAL 17 DAY) AS pay_date, '住院 2 天（先行垫付，认定后向基金申报）' AS remark
    UNION ALL SELECT 99621, 9962, 'MEDICAL', 620.00, 'COMPANY', DATE_SUB(CURDATE(), INTERVAL 26 DAY), '清创缝合、破伤风针'
    UNION ALL SELECT 99631, 9963, 'MEDICAL', 18650.00, 'FUND', DATE_SUB(CURDATE(), INTERVAL 180 DAY), '住院手术（内固定）'
    UNION ALL SELECT 99632, 9963, 'FOOD', 600.00, 'FUND', DATE_SUB(CURDATE(), INTERVAL 180 DAY), '住院伙食补助 20 天'
    UNION ALL SELECT 99633, 9963, 'LEAVE_WAGE', 12600.00, 'COMPANY', DATE_SUB(CURDATE(), INTERVAL 150 DAY), '停工留薪期 3 个月工资'
    UNION ALL SELECT 99634, 9963, 'LUMP_SUM', 29400.00, 'FUND', DATE_SUB(CURDATE(), INTERVAL 100 DAY), '十级伤残 7 个月本人工资'
) v
WHERE EXISTS (SELECT 1 FROM injury_case c WHERE c.id = v.case_id)
  AND NOT EXISTS (SELECT 1 FROM injury_cost x WHERE x.id = v.id);
