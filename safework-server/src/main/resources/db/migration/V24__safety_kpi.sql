-- 第二十九部分：安全目标与绩效考核（年度目标 + 权重，实际值由系统从各模块自动统计）

CREATE TABLE kpi_target (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    year          INT           NOT NULL,
    metric_code   VARCHAR(20)   NOT NULL COMMENT '指标编码，见 KpiRules.METRICS',
    target_value  DOUBLE        NOT NULL COMMENT '目标值（比例类 %，次数类为上限）',
    weight        INT           NOT NULL COMMENT '权重，一年的合计 100',
    updater_name  VARCHAR(50)   NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_year_metric (tenant_id, year, metric_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度安全目标';

-- 菜单：安全管理下面加【安全目标】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(317,  3,   '安全目标', 'C', 'T', '/safety/kpi', 'safety/kpi/index', 'kpi:list', 'kpi', 0, NOW(), NOW()),
(3171, 317, '设置年度目标', 'F', 'T', NULL, NULL, 'kpi:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',317,3171')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('317', menu_ids) = 0;

-- 安全员查看和设置；企业管理员本来就有全部权限
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (317, 3171)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示企业今年的目标（和系统默认目标一样，权重合计 100）
INSERT INTO kpi_target (id, tenant_id, year, metric_code, target_value, weight, updater_name, create_time, update_time) VALUES
(9701, 1, YEAR(CURDATE()), 'HAZARD_ONTIME', 95, 20, '演示企业管理员', NOW(), NOW()),
(9702, 1, YEAR(CURDATE()), 'HAZARD_CLOSE', 90, 10, '演示企业管理员', NOW(), NOW()),
(9703, 1, YEAR(CURDATE()), 'CHECK_ONTIME', 95, 15, '演示企业管理员', NOW(), NOW()),
(9704, 1, YEAR(CURDATE()), 'MEETING_RATE', 90, 10, '演示企业管理员', NOW(), NOW()),
(9705, 1, YEAR(CURDATE()), 'TRAINING_PASS', 90, 10, '演示企业管理员', NOW(), NOW()),
(9706, 1, YEAR(CURDATE()), 'RESP_SIGN', 100, 10, '演示企业管理员', NOW(), NOW()),
(9707, 1, YEAR(CURDATE()), 'EQUIP_ONTIME', 100, 10, '演示企业管理员', NOW(), NOW()),
(9708, 1, YEAR(CURDATE()), 'ACCIDENT', 0, 15, '演示企业管理员', NOW(), NOW());
