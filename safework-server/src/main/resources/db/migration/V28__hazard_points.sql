-- 第三十三部分：隐患随手拍积分（隐患被确认、按期整改自动加分 → 排行榜 → 兑换奖品，发放金额记入安全投入）

CREATE TABLE point_account (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '企业',
    user_id       BIGINT       NOT NULL,
    user_name     VARCHAR(50)  NOT NULL,
    balance       INT          NOT NULL DEFAULT 0 COMMENT '当前余额',
    earned        INT          NOT NULL DEFAULT 0 COMMENT '累计得到',
    create_time   DATETIME     NULL,
    update_time   DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_user (tenant_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分账户';

CREATE TABLE point_record (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    user_id        BIGINT        NOT NULL,
    user_name      VARCHAR(50)   NOT NULL,
    points         INT           NOT NULL COMMENT '变动（正数得分，负数扣减）',
    balance_after  INT           NOT NULL COMMENT '变动后余额',
    source_type    VARCHAR(16)   NOT NULL COMMENT 'HAZARD_REPORT / HAZARD_RECTIFY / MANUAL / REDEEM / REDEEM_RETURN',
    source_id      BIGINT        NULL COMMENT '隐患 ID / 兑换 ID',
    reason         VARCHAR(200)  NOT NULL,
    operator_name  VARCHAR(50)   NULL,
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_source (tenant_id, source_type, source_id, user_id, deleted) COMMENT '同一来源同一个人只记一次（手工调整 source_id 为空不受限）',
    KEY idx_tenant_user (tenant_id, user_id, create_time),
    KEY idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';

CREATE TABLE point_reward (
    id            BIGINT         NOT NULL COMMENT '主键',
    tenant_id     BIGINT         NOT NULL COMMENT '企业',
    name          VARCHAR(40)    NOT NULL,
    points        INT            NOT NULL COMMENT '所需积分',
    price         DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '单价（元），发放时记入安全投入',
    stock         INT            NOT NULL DEFAULT 0,
    status        TINYINT        NOT NULL DEFAULT 1,
    remark        VARCHAR(100)   NULL,
    create_time   DATETIME       NULL,
    update_time   DATETIME       NULL,
    deleted       TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分奖品';

CREATE TABLE point_redeem (
    id               BIGINT         NOT NULL COMMENT '主键',
    tenant_id        BIGINT         NOT NULL COMMENT '企业',
    redeem_no        VARCHAR(20)    NOT NULL,
    user_id          BIGINT         NOT NULL,
    user_name        VARCHAR(50)    NOT NULL,
    reward_id        BIGINT         NOT NULL,
    reward_name      VARCHAR(40)    NOT NULL,
    points           INT            NOT NULL,
    price            DECIMAL(10, 2) NOT NULL DEFAULT 0,
    status           VARCHAR(10)    NOT NULL COMMENT 'PENDING 待发放 / ISSUED 已发放 / REJECTED 已退回',
    handler_name     VARCHAR(50)    NULL,
    handle_time      DATETIME       NULL,
    remark           VARCHAR(100)   NULL,
    cost_expense_id  BIGINT         NULL COMMENT '发放时记入安全投入的支出记录',
    create_time      DATETIME       NULL,
    update_time      DATETIME       NULL,
    deleted          TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换';

-- 菜单：安全管理下面加【随手拍积分】（员工在手机“我的 → 我的积分”里看积分、兑换，不需要菜单）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(320,  3,   '随手拍积分', 'C', 'T', '/safety/point', 'safety/point/index', 'point:list', 'point', 2, NOW(), NOW()),
(3201, 320, '调整积分、管理奖品、发放', 'F', 'T', NULL, NULL, 'point:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',320,3201')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('320', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (320, 3201)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ===== 演示数据：按已有隐患的处理记录补发积分 =====
-- 上报人：已经被确认（指派过）的隐患，一般 10 分、重大 50 分，时间 = 指派时间
INSERT INTO point_record (id, tenant_id, user_id, user_name, points, balance_after, source_type, source_id, reason, operator_name, create_time, update_time)
SELECT 95000000 + h.id, h.tenant_id, h.reporter_id, h.reporter_name, IF(h.level = 'MAJOR', 50, 10), 0, 'HAZARD_REPORT', h.id,
       CONCAT('上报的隐患 ', h.hazard_no, '《', h.title, '》被确认为', IF(h.level = 'MAJOR', '重大', '一般'), '隐患'),
       l.operator_name, l.create_time, l.create_time
FROM hazard h JOIN hazard_log l ON l.hazard_id = h.id AND l.action = 'ASSIGN'
WHERE h.tenant_id = 1 AND h.deleted = 0;

-- 整改人：已验收闭环且按期整改的 +5，时间 = 验收时间
INSERT INTO point_record (id, tenant_id, user_id, user_name, points, balance_after, source_type, source_id, reason, operator_name, create_time, update_time)
SELECT 96000000 + h.id, h.tenant_id, h.rectifier_id, h.rectifier_name, 5, 0, 'HAZARD_RECTIFY', h.id,
       CONCAT('按期完成隐患 ', h.hazard_no, '《', h.title, '》的整改'), h.verifier_name, h.verify_time, h.verify_time
FROM hazard h
WHERE h.tenant_id = 1 AND h.deleted = 0 AND h.status = 'CLOSED' AND h.rectifier_id IS NOT NULL
  AND h.rectify_time IS NOT NULL AND h.deadline IS NOT NULL AND h.rectify_time <= h.deadline;

-- 手工调整、兑换
INSERT INTO point_record (id, tenant_id, user_id, user_name, points, balance_after, source_type, source_id, reason, operator_name, create_time, update_time) VALUES
(97000001, 1, 104, '赵六（监护人）', 20, 0, 'MANUAL', NULL, '提出罐区呼吸阀巡检路线优化建议，被采纳', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY)),
(97000002, 1, 103, '李四（作业负责人）', -10, 0, 'MANUAL', NULL, '进入罐区未交出手机（违章），按积分办法扣分', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(97000003, 1, 103, '李四（作业负责人）', -100, 0, 'REDEEM', 9811, '兑换 不锈钢保温杯（DH-DEMO-0001）', '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY)),
(97000004, 1, 104, '赵六（监护人）', -60, 0, 'REDEEM', 9812, '兑换 劳保手套套装（DH-DEMO-0002）', '赵六（监护人）', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 每条流水的“变动后余额”：按时间累加
UPDATE point_record r JOIN (
    SELECT id, SUM(points) OVER (PARTITION BY user_id ORDER BY create_time, id) AS b FROM point_record WHERE tenant_id = 1
) x ON x.id = r.id
SET r.balance_after = x.b
WHERE r.tenant_id = 1;

INSERT INTO point_account (id, tenant_id, user_id, user_name, balance, earned, create_time, update_time)
SELECT 9800 + user_id - 100, 1, user_id, MAX(user_name), SUM(points), SUM(IF(points > 0 AND source_type <> 'REDEEM_RETURN', points, 0)), MIN(create_time), NOW()
FROM point_record WHERE tenant_id = 1 GROUP BY user_id;

INSERT INTO point_reward (id, tenant_id, name, points, price, stock, status, remark, create_time, update_time) VALUES
(9801, 1, '劳保手套套装', 60, 20, 30, 1, '防割手套 + 丁腈手套各 3 双', NOW(), NOW()),
(9802, 1, '不锈钢保温杯', 100, 35, 19, 1, NULL, NOW(), NOW()),
(9803, 1, '充电宝 10000mAh', 300, 89, 10, 1, NULL, NOW(), NOW()),
(9804, 1, '超市购物卡 50 元', 500, 50, 10, 1, NULL, NOW(), NOW()),
(9805, 1, '“安全之星”表彰 + 带薪休假半天', 800, 0, 5, 1, '不花钱，由人事部安排', NOW(), NOW());

INSERT INTO point_redeem (id, tenant_id, redeem_no, user_id, user_name, reward_id, reward_name, points, price, status, handler_name, handle_time, remark, cost_expense_id, create_time, update_time) VALUES
(9811, 1, 'DH-DEMO-0001', 103, '李四（作业负责人）', 9802, '不锈钢保温杯', 100, 35, 'ISSUED', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 19 DAY), NULL, NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9812, 1, 'DH-DEMO-0002', 104, '赵六（监护人）', 9801, '劳保手套套装', 60, 20, 'PENDING', NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));
