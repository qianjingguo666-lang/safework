-- V34：未遂事件与安全观察，记录险情、人员行为和现场状态，支持分析、改进及转隐患
CREATE TABLE safety_observation (
    id                  BIGINT       NOT NULL COMMENT '主键',
    tenant_id           BIGINT       NOT NULL COMMENT '企业',
    observation_no      VARCHAR(30)  NOT NULL COMMENT '观察编号',
    observation_type    VARCHAR(24)  NOT NULL COMMENT 'NEAR_MISS / UNSAFE_BEHAVIOR / UNSAFE_CONDITION / GOOD_PRACTICE',
    title               VARCHAR(100) NOT NULL,
    occur_time          DATETIME     NOT NULL,
    location            VARCHAR(100) NOT NULL,
    description         VARCHAR(1000) NOT NULL,
    potential_level     VARCHAR(10)  NOT NULL COMMENT 'LOW / MEDIUM / HIGH',
    immediate_action    VARCHAR(500) NULL COMMENT '现场立即采取的措施',
    root_cause          VARCHAR(1000) NULL,
    corrective_action   VARCHAR(1000) NULL,
    status              VARCHAR(12)  NOT NULL COMMENT 'REPORTED / ANALYZING / CLOSED',
    hazard_id           BIGINT       NULL COMMENT '转入隐患闭环后的隐患 ID',
    reporter_id         BIGINT       NOT NULL,
    reporter_name       VARCHAR(50)  NOT NULL,
    analyst_id          BIGINT       NULL,
    analyst_name        VARCHAR(50)  NULL,
    analyzed_time       DATETIME     NULL,
    closed_time         DATETIME     NULL,
    version             INT          NOT NULL DEFAULT 0,
    create_time         DATETIME     NULL,
    update_time         DATETIME     NULL,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_observation_no (tenant_id, observation_no, deleted),
    KEY idx_observation_status_time (tenant_id, status, occur_time),
    KEY idx_observation_reporter (tenant_id, reporter_id, occur_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未遂事件与安全观察';

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(324, 3, '未遂事件与安全观察', 'C', 'T', '/safety/observation', 'safety/observation/index', 'safety:observation:list', 'observation', 1, NOW(), NOW()),
(3241, 324, '分析与关闭观察', 'F', 'T', NULL, NULL, 'safety:observation:manage', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package
SET menu_ids = CONCAT(menu_ids, ',324,3241')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('324', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r
JOIN sys_menu m ON (
    (r.role_code = 'safety' AND m.id IN (324, 3241))
    OR (r.role_code IN ('leader', 'guardian') AND m.id = 324)
)
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示数据：一条已分析待整改的险情，一条待安全员分析的安全观察
INSERT INTO safety_observation
(id, tenant_id, observation_no, observation_type, title, occur_time, location, description, potential_level, immediate_action,
 root_cause, corrective_action, status, reporter_id, reporter_name, analyst_id, analyst_name, analyzed_time, create_time, update_time)
SELECT 34001, 1, 'GC202611-34001', 'NEAR_MISS', '叉车倒车险些碰到行人', DATE_SUB(NOW(), INTERVAL 2 DAY), '成品仓库西侧通道',
       '叉车倒车时后方视线被货物遮挡，行人及时避让，未造成人员伤害或财产损失。', 'HIGH',
       '暂停该区域叉车作业并疏散行人，现场设置临时警戒线。',
       '人车通道未有效隔离，货物堆放遮挡驾驶员视线。', '划分并标识人车分流路线；调整货物堆放高度；班前会重申倒车指挥要求。',
       'ANALYZING', 104, '赵六（监护人）', 102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 1 DAY),
       DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE NOT EXISTS (SELECT 1 FROM safety_observation WHERE id = 34001);
