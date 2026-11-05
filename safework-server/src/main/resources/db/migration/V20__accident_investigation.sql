-- 第二十五部分：事故调查处理（“四不放过”：原因未查清不放过、责任人员未处理不放过、整改措施未落实不放过、有关人员未受到教育不放过）

CREATE TABLE accident_investigation (
    id                BIGINT         NOT NULL COMMENT '主键',
    tenant_id         BIGINT         NOT NULL COMMENT '企业',
    event_id          BIGINT         NOT NULL COMMENT '事故快报（emergency_event）',
    event_no          VARCHAR(30)    NOT NULL,
    event_title       VARCHAR(100)   NOT NULL,
    accident_type     VARCHAR(20)    NULL,
    level             VARCHAR(10)    NULL,
    occur_time        DATETIME       NOT NULL,
    status            VARCHAR(16)    NOT NULL DEFAULT 'INVESTIGATING' COMMENT 'INVESTIGATING 调查中 / CLOSED 已结案',
    leader_id         BIGINT         NOT NULL COMMENT '调查组长',
    leader_name       VARCHAR(50)    NOT NULL,
    members           VARCHAR(200)   NULL COMMENT '调查组成员',
    deadline          DATE           NOT NULL COMMENT '调查期限',
    direct_cause      VARCHAR(1000)  NULL,
    indirect_cause    VARCHAR(1000)  NULL,
    management_cause  VARCHAR(1000)  NULL,
    economic_loss     DECIMAL(12, 2) NULL COMMENT '直接经济损失（万元）',
    lessons           VARCHAR(1000)  NULL COMMENT '事故教训',
    education_date    DATE           NULL COMMENT '警示教育日期',
    education_count   INT            NULL COMMENT '参加人数',
    education_note    VARCHAR(500)   NULL,
    starter_name      VARCHAR(50)    NULL,
    close_time        DATETIME       NULL,
    closer_name       VARCHAR(50)    NULL,
    to_knowledge      TINYINT        NOT NULL DEFAULT 0 COMMENT '结案时是否写进 AI 知识库',
    version           INT            NOT NULL DEFAULT 0,
    create_time       DATETIME       NULL,
    update_time       DATETIME       NULL,
    deleted           TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event (event_id, deleted),
    KEY idx_tenant_status (tenant_id, status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事故调查';

CREATE TABLE accident_measure (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '企业',
    investigation_id  BIGINT        NOT NULL,
    content           VARCHAR(300)  NOT NULL,
    owner_id          BIGINT        NOT NULL COMMENT '整改责任人',
    owner_name        VARCHAR(50)   NOT NULL,
    deadline          DATE          NOT NULL,
    status            VARCHAR(8)    NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 进行中 / DONE 已完成',
    done_note         VARCHAR(300)  NULL COMMENT '落实情况',
    done_time         DATETIME      NULL,
    done_by           VARCHAR(50)   NULL,
    version           INT           NOT NULL DEFAULT 0,
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_inv (tenant_id, investigation_id),
    KEY idx_tenant_owner (tenant_id, owner_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事故整改措施';

CREATE TABLE accident_punish (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '企业',
    investigation_id  BIGINT        NOT NULL,
    person_name       VARCHAR(50)   NOT NULL,
    duty              VARCHAR(12)   NOT NULL COMMENT 'DIRECT 直接责任 / MAIN 主要责任 / LEADER 领导责任 / MANAGEMENT 管理责任',
    measure           VARCHAR(200)  NOT NULL COMMENT '处理措施',
    recorder_name     VARCHAR(50)   NULL,
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_inv (tenant_id, investigation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事故责任处理';

-- 菜单：安全管理下面加【事故调查】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(313,  3,   '事故调查', 'C', 'T', '/safety/accident', 'safety/accident/index', 'accident:list', 'accident', 14, NOW(), NOW()),
(3131, 313, '启动调查、结案', 'F', 'T', NULL, NULL, 'accident:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',313,3131')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('313', menu_ids) = 0;

-- 安全员管理；作业负责人、监护人能看（被指定为调查组长时能填写，整改责任人填写落实情况）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (313, 3131)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 313
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示：一起轻伤事故（硫化氢中毒）正在调查——原因已查明、责任已处理，整改措施 1 项完成、1 项快到期、1 项逾期，还没开展警示教育
INSERT INTO emergency_event (id, tenant_id, event_no, title, accident_type, level, location, space_id, space_name, description, dead, serious_injured, minor_injured,
                             occur_time, reporter_id, reporter_name, status, plan_id, plan_title, commander_id, commander_name, response_time, close_time, summary, version, create_time, update_time) VALUES
(9402, 1, CONCAT('YJ', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '%Y%m%d'), '-0002'), '1# 污水调节池清淤作业人员硫化氢中毒', 'POISONING', 'GENERAL',
 '污水处理站 1# 调节池', NULL, NULL,
 '承包商作业人员进入 1# 调节池清淤约 10 分钟后出现头晕、恶心，监护人发现后立即启动救援，将其拉出池外送医，诊断为轻度硫化氢中毒，住院观察 2 天后出院。', 0, 0, 1,
 TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '14:20:00'), 104, '赵六（监护人）', 'CLOSED', NULL, NULL, 102, '张三（安全员）',
 TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '14:25:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '16:00:00'),
 '伤者送医，现场通风后停止作业，等待事故调查。', 2, NOW(), NOW());

INSERT INTO accident_investigation (id, tenant_id, event_id, event_no, event_title, accident_type, level, occur_time, status, leader_id, leader_name, members, deadline,
                                    direct_cause, indirect_cause, management_cause, economic_loss, lessons, education_date, education_count, education_note,
                                    starter_name, to_knowledge, version, create_time, update_time)
SELECT 9951, 1, e.id, e.event_no, e.title, e.accident_type, e.level, e.occur_time, 'INVESTIGATING', 102, '张三（安全员）', '李四（作业负责人）、设备部、承包商项目经理',
       DATE_ADD(DATE(e.occur_time), INTERVAL 30 DAY),
       '作业人员进入调节池前未按规定进行气体检测，清淤搅动底泥释放硫化氢，作业人员未佩戴正压式空气呼吸器。',
       '调节池未设置固定式硫化氢检测报警仪；承包商作业人员对受限空间风险认识不足。',
       '受限空间作业票审批时未核实气体检测记录；承包商入厂安全培训未覆盖受限空间专项内容；监护人同时兼顾其他工作。',
       1.80, '受限空间作业必须“先通风、再检测、后作业”，检测不合格严禁进入；清淤等会扰动底泥的作业要连续检测。', NULL, NULL, NULL,
       '演示企业管理员', 0, 3, NOW(), NOW()
FROM emergency_event e WHERE e.id = 9402;

INSERT INTO accident_measure (id, tenant_id, investigation_id, content, owner_id, owner_name, deadline, status, done_note, done_time, done_by, version, create_time, update_time) VALUES
(9961, 1, 9951, '受限空间作业票审批增加“气体检测记录拍照上传”必填项，审批人核实后才能批准', 102, '张三（安全员）', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'DONE',
 '已在作业票模板中增加气体检测必填项并培训审批人', DATE_SUB(NOW(), INTERVAL 7 DAY), '张三（安全员）', 1, NOW(), NOW()),
(9962, 1, 9951, '1#、2# 调节池安装固定式硫化氢检测报警仪，报警信号接入中控室', 103, '李四（作业负责人）', DATE_ADD(CURDATE(), INTERVAL 2 DAY), 'OPEN',
 NULL, NULL, NULL, 0, NOW(), NOW()),
(9963, 1, 9951, '对承包商全部作业人员补充受限空间专项培训并考试，不合格不得入厂', 104, '赵六（监护人）', DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'OPEN',
 NULL, NULL, NULL, 0, NOW(), NOW());

INSERT INTO accident_punish (id, tenant_id, investigation_id, person_name, duty, measure, recorder_name, create_time, update_time) VALUES
(9971, 1, 9951, '承包商作业班长 王某', 'DIRECT', '未组织气体检测即安排人员进入，清退出厂，承包商扣 10 分', '张三（安全员）', NOW(), NOW()),
(9972, 1, 9951, '作业票审批人', 'MANAGEMENT', '审批时未核实气体检测记录，通报批评并重新参加审批人培训', '张三（安全员）', NOW(), NOW());
