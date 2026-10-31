-- 第十七部分：人员资质（证书台账、到期提醒、开票持证校验）

CREATE TABLE person_cert (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    user_id       BIGINT        NOT NULL COMMENT '持证人',
    user_name     VARCHAR(50)   NOT NULL,
    dept_name     VARCHAR(50)   NULL,
    cert_type     VARCHAR(20)   NOT NULL COMMENT '证书类型',
    cert_no       VARCHAR(50)   NOT NULL COMMENT '证书编号',
    issuer        VARCHAR(60)   NULL COMMENT '发证机关',
    issue_date    DATE          NOT NULL,
    expire_date   DATE          NULL COMMENT '有效期至，空表示长期',
    review_date   DATE          NULL COMMENT '下次复审日期',
    valid_until   DATE          NULL COMMENT '实际可用到：有效期和复审日期取早的',
    remark        VARCHAR(200)  NULL,
    updater_name  VARCHAR(50)   NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_tenant_valid (tenant_id, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员资质证书';

CREATE TABLE cert_requirement (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    ticket_type   VARCHAR(20)   NOT NULL COMMENT 'CONFINED_SPACE / HOT_WORK / HEIGHT',
    holder_role   VARCHAR(10)   NOT NULL COMMENT 'APPLICANT 作业负责人 / GUARDIAN 监护人',
    cert_type     VARCHAR(20)   NOT NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_type (tenant_id, ticket_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开票持证要求';

-- 菜单：安全管理下面加【人员资质】；所有人能看自己的证书，安全员管理全部
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(306,  3,   '人员资质', 'C', 'T', '/safety/cert', 'safety/cert/index', 'cert:list', 'cert', 7, NOW(), NOW()),
(3061, 306, '管理证书和持证要求', 'F', 'T', NULL, NULL, 'cert:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',306,3061')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('306', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (306, 3061)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 306
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示化工的开票持证要求：作业负责人持对应的作业证，监护人持监护人培训合格证
-- （其他企业默认不校验，安全员在【人员资质 → 开票持证要求】里勾选后生效）
INSERT INTO cert_requirement (id, tenant_id, ticket_type, holder_role, cert_type, create_time, update_time) VALUES
(9701, 1, 'CONFINED_SPACE', 'APPLICANT', 'CONFINED_SPACE', NOW(), NOW()),
(9702, 1, 'CONFINED_SPACE', 'GUARDIAN',  'GUARDIAN',       NOW(), NOW()),
(9703, 1, 'CONFINED_SPACE', 'GUARDIAN',  'CONFINED_SPACE', NOW(), NOW()),
(9704, 1, 'HOT_WORK',       'APPLICANT', 'WELDING',        NOW(), NOW()),
(9705, 1, 'HOT_WORK',       'GUARDIAN',  'GUARDIAN',       NOW(), NOW()),
(9706, 1, 'HEIGHT',         'APPLICANT', 'HEIGHT',         NOW(), NOW()),
(9707, 1, 'HEIGHT',         'GUARDIAN',  'GUARDIAN',       NOW(), NOW());

-- 演示证书：作业负责人、监护人的证书都有效（演示作业票能正常开）；
-- 安全员的资格证 20 天后到期、监护人的电工证复审逾期，用来展示到期提醒（不影响开票）
INSERT INTO person_cert (id, tenant_id, user_id, user_name, cert_type, cert_no, issuer, issue_date, expire_date, review_date, valid_until, remark, updater_name, create_time, update_time) VALUES
(9601, 1, 103, '李四（作业负责人）', 'CONFINED_SPACE', 'YX-2025-0318', '合肥市应急管理局',
 DATE_SUB(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 2 YEAR), NULL, DATE_ADD(CURDATE(), INTERVAL 2 YEAR), '有效期 3 年', '张三（安全员）', NOW(), NOW()),
(9602, 1, 103, '李四（作业负责人）', 'WELDING', 'T340102199001011234', '安徽省应急管理厅',
 DATE_SUB(CURDATE(), INTERVAL 2 YEAR), DATE_ADD(CURDATE(), INTERVAL 4 YEAR), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), '特种作业操作证 6 年有效，每 3 年复审', '张三（安全员）', NOW(), NOW()),
(9603, 1, 103, '李四（作业负责人）', 'HEIGHT', 'T340102199001015678', '安徽省应急管理厅',
 DATE_SUB(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 5 YEAR), DATE_ADD(CURDATE(), INTERVAL 2 YEAR), DATE_ADD(CURDATE(), INTERVAL 2 YEAR), NULL, '张三（安全员）', NOW(), NOW()),
(9604, 1, 104, '赵六（监护人）', 'GUARDIAN', 'JH-2026-0021', '演示化工安全环保部',
 DATE_SUB(CURDATE(), INTERVAL 6 MONTH), DATE_ADD(CURDATE(), INTERVAL 30 MONTH), NULL, DATE_ADD(CURDATE(), INTERVAL 30 MONTH), '企业内部培训考核合格', '张三（安全员）', NOW(), NOW()),
(9605, 1, 104, '赵六（监护人）', 'CONFINED_SPACE', 'YX-2025-0412', '合肥市应急管理局',
 DATE_SUB(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 2 YEAR), NULL, DATE_ADD(CURDATE(), INTERVAL 2 YEAR), NULL, '张三（安全员）', NOW(), NOW()),
(9606, 1, 104, '赵六（监护人）', 'ELECTRICIAN', 'T340102198805052222', '安徽省应急管理厅',
 DATE_SUB(CURDATE(), INTERVAL 40 MONTH), DATE_ADD(CURDATE(), INTERVAL 32 MONTH), DATE_SUB(CURDATE(), INTERVAL 4 MONTH), DATE_SUB(CURDATE(), INTERVAL 4 MONTH), '复审已逾期，不能从事电工作业', '张三（安全员）', NOW(), NOW()),
(9607, 1, 102, '张三（安全员）', 'SAFETY_MANAGER', 'AQ-2023-1107', '合肥市应急管理局',
 DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 20 DAY), INTERVAL 3 YEAR), DATE_ADD(CURDATE(), INTERVAL 20 DAY), NULL, DATE_ADD(CURDATE(), INTERVAL 20 DAY), '有效期 3 年，到期前需参加再培训', '张三（安全员）', NOW(), NOW()),
(9608, 1, 101, '演示企业管理员', 'HEIGHT', 'T340102198512123333', '安徽省应急管理厅',
 DATE_SUB(CURDATE(), INTERVAL 2 YEAR), DATE_ADD(CURDATE(), INTERVAL 4 YEAR), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), NULL, '张三（安全员）', NOW(), NOW()),
-- 测试冶金：王五的有限空间证（用来验证租户隔离）
(9651, 2, 202, '王五（监护人）', 'CONFINED_SPACE', 'YX-2026-0099', '马鞍山市应急管理局',
 DATE_SUB(CURDATE(), INTERVAL 3 MONTH), DATE_ADD(CURDATE(), INTERVAL 33 MONTH), NULL, DATE_ADD(CURDATE(), INTERVAL 33 MONTH), NULL, '测试企业管理员', NOW(), NOW());

-- 部门名称（用户表里的部门，有的话带上）
UPDATE person_cert c JOIN sys_user u ON u.id = c.user_id LEFT JOIN sys_dept d ON d.id = u.dept_id
SET c.dept_name = d.dept_name
WHERE c.id BETWEEN 9601 AND 9651;
