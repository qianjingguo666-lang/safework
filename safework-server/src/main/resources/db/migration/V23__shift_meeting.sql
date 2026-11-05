-- 第二十八部分：班前会 / 班组安全活动（班组 → 班前会：安全要点、点名、身体精神状态 → 成员确认 → 开展率）

CREATE TABLE meeting_team (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    name          VARCHAR(30)   NOT NULL,
    leader_id     BIGINT        NOT NULL COMMENT '班组长',
    leader_name   VARCHAR(50)   NOT NULL,
    status        TINYINT       NOT NULL DEFAULT 1,
    remark        VARCHAR(200)  NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_leader (tenant_id, leader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班组';

CREATE TABLE meeting_team_member (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    team_id       BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    user_name     VARCHAR(50)   NOT NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_team (tenant_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班组成员';

CREATE TABLE shift_meeting (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    team_id        BIGINT        NOT NULL,
    team_name      VARCHAR(30)   NOT NULL,
    meeting_date   DATE          NOT NULL,
    shift          VARCHAR(8)    NOT NULL COMMENT 'DAY 白班 / MIDDLE 中班 / NIGHT 夜班',
    host_id        BIGINT        NOT NULL,
    host_name      VARCHAR(50)   NOT NULL,
    topics         VARCHAR(2000) NOT NULL COMMENT '安全要点',
    ai_used        TINYINT       NOT NULL DEFAULT 0,
    member_count   INT           NOT NULL DEFAULT 0,
    present_count  INT           NOT NULL DEFAULT 0,
    unfit_count    INT           NOT NULL DEFAULT 0 COMMENT '出席但状态不好、不能上岗的人数',
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_date_shift (team_id, meeting_date, shift, deleted),
    KEY idx_tenant_date (tenant_id, meeting_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班前会';

CREATE TABLE meeting_attendee (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    meeting_id     BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    user_name      VARCHAR(50)   NOT NULL,
    attend         VARCHAR(8)    NOT NULL COMMENT 'PRESENT 出席 / LEAVE 请假 / ABSENT 缺席',
    body_state     VARCHAR(8)    NULL COMMENT 'GOOD 良好 / UNWELL 身体不适 / TIRED 精神疲劳 / DRINK 饮酒',
    note           VARCHAR(100)  NULL,
    confirm_time   DATETIME      NULL COMMENT '本人确认时间',
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_meeting (tenant_id, meeting_id),
    KEY idx_tenant_user (tenant_id, user_id, confirm_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班前会点名';

-- 菜单：安全管理下面加【班前会】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(316,  3,   '班前会', 'C', 'T', '/safety/meeting', 'safety/meeting/index', 'meeting:list', 'meeting', 17, NOW(), NOW()),
(3161, 316, '管理班组、代开班前会', 'F', 'T', NULL, NULL, 'meeting:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',316,3161')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('316', menu_ids) = 0;

-- 安全员管理班组；作业负责人、监护人能看（是班组长的能开自己班组的班前会）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (316, 3161)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 316
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示班组：罐区操作班（班组长李四，成员李四、赵六）；安全管理组（班组长张三，成员张三、赵六）
INSERT INTO meeting_team (id, tenant_id, name, leader_id, leader_name, status, remark, create_time, update_time) VALUES
(9601, 1, '罐区操作班', 103, '李四（作业负责人）', 1, '白班 8:00 开工', NOW(), NOW()),
(9602, 1, '安全管理组', 102, '张三（安全员）', 1, NULL, NOW(), NOW());
INSERT INTO meeting_team_member (id, tenant_id, team_id, user_id, user_name, create_time, update_time) VALUES
(9611, 1, 9601, 103, '李四（作业负责人）', NOW(), NOW()),
(9612, 1, 9601, 104, '赵六（监护人）', NOW(), NOW()),
(9613, 1, 9602, 102, '张三（安全员）', NOW(), NOW()),
(9614, 1, 9602, 104, '赵六（监护人）', NOW(), NOW());

-- 演示记录：罐区操作班最近 29 天里每 5 天漏开 1 天（今天还没开，方便演示手机上开班前会）；
-- 安全管理组最近 10 天开了 6 次，今天开了，赵六精神疲劳、不能上岗，还没确认
INSERT INTO shift_meeting (id, tenant_id, team_id, team_name, meeting_date, shift, host_id, host_name, topics, ai_used,
                           member_count, present_count, unfit_count, create_time, update_time)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 29)
SELECT 96000 + n, 1, 9601, '罐区操作班', DATE_SUB(CURDATE(), INTERVAL n DAY), 'DAY', 103, '李四（作业负责人）',
       '1. 罐区今天正常收发料，装卸车前检查静电接地夹\n2. 巡检储罐液位、呼吸阀，发现泄漏立即报告\n3. 进入罐区禁止携带火种和非防爆手机\n4. 正确佩戴防毒半面罩和防静电工作服',
       IF(n % 3 = 0, 1, 0), 2, 2, 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '07:50:00'), NOW()
FROM seq WHERE n % 5 <> 3;

INSERT INTO meeting_attendee (id, tenant_id, meeting_id, user_id, user_name, attend, body_state, note, confirm_time, create_time, update_time)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 29)
SELECT 97000 + n * 2, 1, 96000 + n, 103, '李四（作业负责人）', 'PRESENT', 'GOOD', NULL,
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '07:55:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '07:55:00'), NOW()
FROM seq WHERE n % 5 <> 3
UNION ALL
SELECT 97001 + n * 2, 1, 96000 + n, 104, '赵六（监护人）', 'PRESENT', 'GOOD', NULL,
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '08:02:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '07:55:00'), NOW()
FROM seq WHERE n % 5 <> 3;

INSERT INTO shift_meeting (id, tenant_id, team_id, team_name, meeting_date, shift, host_id, host_name, topics, ai_used,
                           member_count, present_count, unfit_count, create_time, update_time) VALUES
(96100, 1, 9602, '安全管理组', CURDATE(), 'DAY', 102, '张三（安全员）',
 '1. 今天污水站有受限空间作业，监护人全程不得离开，每 30 分钟复测一次气体\n2. 罐区 T-102 法兰更换垫片，动火前确认可燃气体检测合格\n3. 上午检查承包商入厂培训记录\n4. 赵六昨晚值夜班精神不佳，今天不安排现场监护',
 1, 2, 2, 1, TIMESTAMP(CURDATE(), '08:10:00'), NOW()),
(96101, 1, 9602, '安全管理组', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'DAY', 102, '张三（安全员）', '1. 本周开展罐区专项检查\n2. 承包商作业必须持证上岗', 0, 2, 2, 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '08:10:00'), NOW()),
(96102, 1, 9602, '安全管理组', DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'DAY', 102, '张三（安全员）', '1. 检查动火作业票气体检测记录\n2. 雨天高处作业暂停', 0, 2, 1, 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 3 DAY), '08:10:00'), NOW()),
(96103, 1, 9602, '安全管理组', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'DAY', 102, '张三（安全员）', '1. 应急演练前检查空气呼吸器\n2. 演练期间注意人员清点', 0, 2, 2, 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '08:10:00'), NOW()),
(96104, 1, 9602, '安全管理组', DATE_SUB(CURDATE(), INTERVAL 7 DAY), 'DAY', 102, '张三（安全员）', '1. 劳保用品到期更换\n2. 职业健康体检通知', 0, 2, 2, 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 7 DAY), '08:10:00'), NOW()),
(96105, 1, 9602, '安全管理组', DATE_SUB(CURDATE(), INTERVAL 9 DAY), 'DAY', 102, '张三（安全员）', '1. 隐患排查周报\n2. 特种作业证复审提醒', 0, 2, 2, 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 9 DAY), '08:10:00'), NOW());

INSERT INTO meeting_attendee (id, tenant_id, meeting_id, user_id, user_name, attend, body_state, note, confirm_time, create_time, update_time) VALUES
(97200, 1, 96100, 102, '张三（安全员）', 'PRESENT', 'GOOD', NULL, TIMESTAMP(CURDATE(), '08:10:00'), NOW(), NOW()),
(97201, 1, 96100, 104, '赵六（监护人）', 'PRESENT', 'TIRED', '昨晚值夜班', NULL, NOW(), NOW()),
(97202, 1, 96101, 102, '张三（安全员）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(97203, 1, 96101, 104, '赵六（监护人）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), NOW()),
(97204, 1, 96102, 102, '张三（安全员）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), NOW()),
(97205, 1, 96102, 104, '赵六（监护人）', 'LEAVE', NULL, '调休', NULL, NOW(), NOW()),
(97206, 1, 96103, 102, '张三（安全员）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW()),
(97207, 1, 96103, 104, '赵六（监护人）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), NOW()),
(97208, 1, 96104, 102, '张三（安全员）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(97209, 1, 96104, 104, '赵六（监护人）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(97210, 1, 96105, 102, '张三（安全员）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), NOW()),
(97211, 1, 96105, 104, '赵六（监护人）', 'PRESENT', 'GOOD', NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), NOW());
