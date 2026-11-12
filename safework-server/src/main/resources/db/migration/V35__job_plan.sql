-- V35：作业计划管理，提前安排作业窗口、责任人和班组，并可关联作业票跟踪执行
CREATE TABLE job_plan (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '企业',
    plan_no VARCHAR(30) NOT NULL COMMENT '计划编号',
    title VARCHAR(100) NOT NULL COMMENT '作业名称',
    ticket_type VARCHAR(20) NOT NULL COMMENT '作业类型',
    location VARCHAR(100) NOT NULL COMMENT '作业地点',
    work_content VARCHAR(500) NOT NULL COMMENT '作业内容',
    department_name VARCHAR(60) NULL COMMENT '部门/班组',
    responsible_id BIGINT NOT NULL COMMENT '作业负责人',
    responsible_name VARCHAR(50) NOT NULL,
    planned_start_time DATETIME NOT NULL,
    planned_end_time DATETIME NOT NULL,
    actual_start_time DATETIME NULL,
    actual_end_time DATETIME NULL,
    ticket_id BIGINT NULL COMMENT '关联作业票',
    ticket_no VARCHAR(30) NULL,
    status VARCHAR(16) NOT NULL COMMENT 'PLANNED / IN_PROGRESS / COMPLETED / CANCELLED',
    remark VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_job_plan_no (tenant_id, plan_no, deleted),
    KEY idx_job_plan_time (tenant_id, planned_start_time, status),
    KEY idx_job_plan_responsible (tenant_id, responsible_id, status),
    KEY idx_job_plan_ticket (tenant_id, ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业计划';

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(203, 2, '作业计划管理', 'C', 'T', '/ticket/plan', 'ticket/plan/index', 'job:plan:list', 'plan', 2, NOW(), NOW()),
(2031, 203, '管理作业计划', 'F', 'T', NULL, NULL, 'job:plan:manage', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package
SET menu_ids = CONCAT(menu_ids, ',203,2031')
WHERE FIND_IN_SET('200', menu_ids) > 0 AND FIND_IN_SET('203', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (
    (r.role_code = 'safety' AND m.id IN (203, 2031))
    OR (r.role_code = 'leader' AND m.id IN (203, 2031))
    OR (r.role_code = 'guardian' AND m.id = 203)
)
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

INSERT INTO job_plan
(id, tenant_id, plan_no, title, ticket_type, location, work_content, department_name, responsible_id, responsible_name,
 planned_start_time, planned_end_time, status, remark, create_time, update_time)
SELECT 35001, 1, 'JH20261112-0001', '成品仓库输送带检修', 'MAINTENANCE', '成品仓库西侧', '检查并更换输送带损坏托辊，试运行确认防护罩复位',
       '设备部 / 维修班', 103, '李四（作业负责人）',
       DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 9 HOUR,
       DATE_ADD(CURDATE(), INTERVAL 1 DAY) + INTERVAL 12 HOUR,
       'PLANNED', '作业前确认隔离挂牌，按现场风险办理对应作业票。', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM job_plan WHERE id = 35001);
