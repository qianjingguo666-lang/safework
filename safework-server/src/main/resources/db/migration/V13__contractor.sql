-- 第十八部分：承包商管理（准入审核、人员入厂培训、违章扣分和黑名单、作业票进场校验）

CREATE TABLE contractor (
    id               BIGINT        NOT NULL COMMENT '主键',
    tenant_id        BIGINT        NOT NULL COMMENT '企业',
    name             VARCHAR(60)   NOT NULL COMMENT '单位名称',
    credit_code      VARCHAR(18)   NOT NULL COMMENT '统一社会信用代码',
    contact_name     VARCHAR(20)   NOT NULL,
    contact_phone    VARCHAR(20)   NOT NULL,
    business_scope   VARCHAR(200)  NULL COMMENT '业务范围',
    license_expire   DATE          NOT NULL COMMENT '资质证书有效期',
    insurance_expire DATE          NOT NULL COMMENT '安全生产责任险到期日',
    status           VARCHAR(12)   NOT NULL COMMENT 'PENDING / APPROVED / REJECTED / BLACKLISTED',
    status_comment   VARCHAR(200)  NULL COMMENT '最近一次审核 / 拉黑 / 解除的意见',
    reviewer_name    VARCHAR(50)   NULL,
    review_time      DATETIME      NULL,
    remark           VARCHAR(200)  NULL,
    create_time      DATETIME      NULL,
    update_time      DATETIME      NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='承包商';

CREATE TABLE contractor_worker (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    contractor_id  BIGINT        NOT NULL,
    name           VARCHAR(20)   NOT NULL,
    phone          VARCHAR(11)   NULL,
    job_type       VARCHAR(20)   NULL COMMENT '工种',
    training_date  DATE          NULL COMMENT '入厂安全培训日期，有效期 12 个月',
    cert_type      VARCHAR(20)   NULL COMMENT '特种作业证类型（同人员资质）',
    cert_no        VARCHAR(50)   NULL,
    cert_expire    DATE          NULL,
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1 在场 0 离场',
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_contractor (tenant_id, contractor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='承包商作业人员';

CREATE TABLE contractor_violation (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    contractor_id  BIGINT        NOT NULL,
    worker_id      BIGINT        NULL,
    worker_name    VARCHAR(20)   NULL,
    description    VARCHAR(300)  NOT NULL,
    points         INT           NOT NULL COMMENT '扣分 1 ~ 20',
    occur_time     DATETIME      NOT NULL,
    recorder_name  VARCHAR(50)   NULL,
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_contractor_time (tenant_id, contractor_id, occur_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='承包商违章记录';

-- 作业票：承包商作业时记录承包商和作业人员（名字存快照）
ALTER TABLE work_ticket
    ADD COLUMN contractor_id           BIGINT       NULL COMMENT '承包商（本企业作业为空）' AFTER guardian_name,
    ADD COLUMN contractor_name         VARCHAR(60)  NULL AFTER contractor_id,
    ADD COLUMN contractor_worker_ids   VARCHAR(500) NULL COMMENT '承包商作业人员 ID，逗号分隔' AFTER contractor_name,
    ADD COLUMN contractor_worker_names VARCHAR(500) NULL AFTER contractor_worker_ids;

-- 菜单：安全管理下面加【承包商管理】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(307,  3,   '承包商管理', 'C', 'T', '/safety/contractor', 'safety/contractor/index', 'contractor:list', 'contractor', 8, NOW(), NOW()),
(3071, 307, '审核承包商、登记人员和违章', 'F', 'T', NULL, NULL, 'contractor:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',307,3071')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('307', menu_ids) = 0;

-- 安全员全部；作业负责人可以查看（申请作业票时要知道哪些承包商人员能用）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (307, 3071)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 307
WHERE r.role_code = 'leader' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示化工：承包商作业人员也要持证（受限空间 → 有限空间培训证，动火 → 焊工证，高处 → 高处作业证）
INSERT INTO cert_requirement (id, tenant_id, ticket_type, holder_role, cert_type, create_time, update_time) VALUES
(9708, 1, 'CONFINED_SPACE', 'WORKER', 'CONFINED_SPACE', NOW(), NOW()),
(9709, 1, 'HOT_WORK',       'WORKER', 'WELDING',        NOW(), NOW()),
(9710, 1, 'HEIGHT',         'WORKER', 'HEIGHT',         NOW(), NOW());

-- 演示承包商：合格（有一个人培训快到期）、合格但保险快到期且有人培训过期、待审核、黑名单
INSERT INTO contractor (id, tenant_id, name, credit_code, contact_name, contact_phone, business_scope, license_expire, insurance_expire, status, status_comment, reviewer_name, review_time, remark, create_time, update_time) VALUES
(9801, 1, '合肥安达建设工程有限公司', '91340100MA2N0A1B2C', '王经理', '13955100001', '设备检维修、钢结构、高处作业',
 DATE_ADD(CURDATE(), INTERVAL 2 YEAR), DATE_ADD(CURDATE(), INTERVAL 5 MONTH), 'APPROVED', '审核通过', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 6 MONTH), '长期合作单位', DATE_SUB(NOW(), INTERVAL 6 MONTH), NOW()),
(9802, 1, '安徽恒通防腐保温有限公司', '91340100MA2P3C4D5E', '李经理', '13955100002', '防腐保温、管道刷漆',
 DATE_ADD(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'APPROVED', '审核通过', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 4 MONTH), NULL, DATE_SUB(NOW(), INTERVAL 4 MONTH), NOW()),
(9803, 1, '合肥鑫源清洗服务有限公司', '91340100MA2R6F7G8H', '周经理', '13955100003', '储罐清洗、污水池清淤',
 DATE_ADD(CURDATE(), INTERVAL 3 YEAR), DATE_ADD(CURDATE(), INTERVAL 11 MONTH), 'PENDING', NULL, NULL, NULL, '新申请入厂', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
(9804, 1, '合肥速达劳务有限公司', '91340100MA2T9J0K1L', '孙经理', '13955100004', '劳务派遣、零星维修',
 DATE_ADD(CURDATE(), INTERVAL 1 YEAR), DATE_ADD(CURDATE(), INTERVAL 6 MONTH), 'BLACKLISTED', '近 12 个月安全评分 55 分，低于 60 分，自动列入黑名单', '系统', DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 YEAR), NOW()),
(9851, 2, '马鞍山宏达检修有限公司', '91340500MA2U2M3N4P', '刘经理', '13955500001', '炉窑检修',
 DATE_ADD(CURDATE(), INTERVAL 2 YEAR), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 'APPROVED', '审核通过', '测试企业管理员', NOW(), NULL, NOW(), NOW());

INSERT INTO contractor_worker (id, tenant_id, contractor_id, name, phone, job_type, training_date, cert_type, cert_no, cert_expire, status, create_time, update_time) VALUES
(9811, 1, 9801, '王建国', '13800001111', '焊工',   DATE_SUB(CURDATE(), INTERVAL 3 MONTH), 'WELDING', 'T340104198203031111', DATE_ADD(CURDATE(), INTERVAL 3 YEAR), 1, NOW(), NOW()),
(9812, 1, 9801, '刘志强', '13800002222', '架子工', DATE_SUB(CURDATE(), INTERVAL 2 MONTH), 'HEIGHT', 'T340104198507072222', DATE_ADD(CURDATE(), INTERVAL 2 YEAR), 1, NOW(), NOW()),
(9813, 1, 9801, '孙立',   '13800003333', '清洗工', DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 12 MONTH), INTERVAL 20 DAY), 'CONFINED_SPACE', 'YX-2025-1203', DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 1, NOW(), NOW()),
(9814, 1, 9802, '陈刚',   '13800004444', '防腐工', DATE_SUB(CURDATE(), INTERVAL 14 MONTH), NULL, NULL, NULL, 1, NOW(), NOW()),
(9815, 1, 9802, '赵明',   '13800005555', '防腐工', DATE_SUB(CURDATE(), INTERVAL 1 MONTH), 'HEIGHT', 'T340104199001015555', DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 1, NOW(), NOW()),
(9816, 1, 9803, '周涛',   '13800006666', '清洗工', DATE_SUB(CURDATE(), INTERVAL 7 DAY), 'CONFINED_SPACE', 'YX-2026-0517', DATE_ADD(CURDATE(), INTERVAL 3 YEAR), 1, NOW(), NOW()),
(9817, 1, 9804, '钱多',   NULL,          '杂工',   DATE_SUB(CURDATE(), INTERVAL 5 MONTH), NULL, NULL, NULL, 1, NOW(), NOW()),
(9861, 2, 9851, '马强',   '13800007777', '炉窑工', DATE_SUB(CURDATE(), INTERVAL 1 MONTH), NULL, NULL, NULL, 1, NOW(), NOW());

INSERT INTO contractor_violation (id, tenant_id, contractor_id, worker_id, worker_name, description, points, occur_time, recorder_name, create_time, update_time) VALUES
(9831, 1, 9801, 9812, '刘志强', '高处作业安全带未高挂低用', 5, DATE_SUB(NOW(), INTERVAL 2 MONTH), '张三（安全员）', NOW(), NOW()),
(9832, 1, 9802, 9814, '陈刚',   '动火作业现场未配备灭火器', 10, DATE_SUB(NOW(), INTERVAL 3 MONTH), '张三（安全员）', NOW(), NOW()),
(9833, 1, 9802, NULL, NULL,     '未按施工方案搭设脚手架，未验收即使用', 10, DATE_SUB(NOW(), INTERVAL 1 MONTH), '张三（安全员）', NOW(), NOW()),
(9834, 1, 9804, 9817, '钱多',   '未检测气体即进入污水池', 20, DATE_SUB(NOW(), INTERVAL 5 MONTH), '张三（安全员）', NOW(), NOW()),
(9835, 1, 9804, 9817, '钱多',   '作业时吸烟', 15, DATE_SUB(NOW(), INTERVAL 3 MONTH), '张三（安全员）', NOW(), NOW()),
(9836, 1, 9804, NULL, NULL,     '无票作业（未办理动火作业票）', 10, DATE_SUB(NOW(), INTERVAL 10 DAY), '张三（安全员）', NOW(), NOW());
