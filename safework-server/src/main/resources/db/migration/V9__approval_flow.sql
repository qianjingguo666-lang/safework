-- 第十四部分：作业票可配置审批流程（多级节点、或签 / 会签）

CREATE TABLE ticket_flow (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    ticket_type   VARCHAR(20)   NOT NULL COMMENT '作业类型',
    nodes         TEXT          NOT NULL COMMENT '审批节点（JSON）',
    updater_name  VARCHAR(50)   NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_type (tenant_id, ticket_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业票审批流程配置';

CREATE TABLE ticket_approval_task (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    ticket_id      BIGINT        NOT NULL,
    round_no       INT           NOT NULL COMMENT '第几次提交',
    node_index     INT           NOT NULL COMMENT '第几个节点（从 0 开始）',
    node_name      VARCHAR(20)   NOT NULL,
    mode           VARCHAR(5)    NOT NULL COMMENT 'ANY 或签 / ALL 会签',
    approver_id    BIGINT        NOT NULL,
    approver_name  VARCHAR(50)   NULL,
    status         VARCHAR(10)   NOT NULL COMMENT 'PENDING / APPROVED / REJECTED / CANCELLED',
    comment        VARCHAR(500)  NULL,
    decide_time    DATETIME      NULL,
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ticket (ticket_id, round_no, node_index),
    KEY idx_approver (tenant_id, approver_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业票审批待办';

ALTER TABLE work_ticket
    ADD COLUMN approval_round INT  NOT NULL DEFAULT 0 COMMENT '第几次提交审批' AFTER status,
    ADD COLUMN current_node   INT  NULL COMMENT '当前审批节点（从 0 开始）' AFTER approval_round,
    ADD COLUMN flow_snapshot  TEXT NULL COMMENT '提交时的审批流程快照' AFTER current_node;

-- 菜单：作业管理下面加【审批流程】（只有企业管理员默认拥有）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(201, 2, '审批流程', 'C', 'T', '/ticket/flow', 'ticket/flow/index', 'ticket:flow:edit', 'flow', 2, NOW(), NOW());
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',201')
WHERE FIND_IN_SET('200', menu_ids) > 0 AND FIND_IN_SET('201', menu_ids) = 0;

-- 演示：演示化工的高处作业配置两级审批（安全员审核 → 企业负责人审批），其他类型仍是默认的单级审批
INSERT INTO ticket_flow (id, tenant_id, ticket_type, nodes, updater_name, create_time, update_time) VALUES
(1, 1, 'HEIGHT', '[{"name":"安全员审核","approverType":"ROLE","roleCode":"safety","userIds":null,"mode":"ANY"},{"name":"企业负责人审批","approverType":"ROLE","roleCode":"admin","userIds":null,"mode":"ANY"}]',
    '演示企业管理员', NOW(), NOW());
