-- V36：作业安全交底，签字确认后才允许开工（启用交底的作业票）
ALTER TABLE work_ticket ADD COLUMN briefing_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否要求安全交底签字';

CREATE TABLE work_briefing (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '企业',
    ticket_id BIGINT NOT NULL COMMENT '作业票',
    ticket_no VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content_snapshot MEDIUMTEXT NOT NULL COMMENT '交底内容快照（作业票/JSA安全措施）',
    status VARCHAR(12) NOT NULL COMMENT 'PENDING / COMPLETED',
    creator_id BIGINT NOT NULL,
    creator_name VARCHAR(50) NOT NULL,
    completed_time DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_briefing_ticket (tenant_id, ticket_id, deleted),
    KEY idx_briefing_status (tenant_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业安全交底';

CREATE TABLE work_briefing_attendee (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '企业',
    briefing_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    participant_name VARCHAR(50) NOT NULL,
    participant_role VARCHAR(16) NOT NULL COMMENT 'APPLICANT / GUARDIAN / MEMBER',
    signed_time DATETIME NULL,
    sign_image MEDIUMTEXT NULL,
    sign_hash CHAR(64) NULL,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_briefing_participant (tenant_id, briefing_id, participant_id, deleted),
    KEY idx_briefing_attendee_user (tenant_id, participant_id, signed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业交底参与人确认';

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(204, 2, '作业安全交底', 'C', 'T', '/ticket/briefing', 'ticket/briefing/index', 'briefing:list', 'briefing', 3, NOW(), NOW()),
(2041, 204, '创建安全交底', 'F', 'T', NULL, NULL, 'briefing:manage', NULL, 1, NOW(), NOW()),
(2042, 204, '签署安全交底', 'F', 'T', NULL, NULL, 'briefing:sign', NULL, 2, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',204,2041,2042')
WHERE FIND_IN_SET('200', menu_ids) > 0 AND FIND_IN_SET('204', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (
    (r.role_code = 'safety' AND m.id IN (204, 2041, 2042))
    OR (r.role_code = 'leader' AND m.id IN (204, 2041, 2042))
    OR (r.role_code = 'guardian' AND m.id IN (204, 2042))
)
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
