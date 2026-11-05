-- 第二十二部分：劳保用品（用品目录和库存 → 岗位发放标准 → 发放登记 → 到期台账）

CREATE TABLE ppe_item (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    name          VARCHAR(40)   NOT NULL COMMENT '用品名称',
    category      VARCHAR(16)   NOT NULL COMMENT '防护部位：HEAD / RESPIRATORY / EYE_FACE / HEARING / HAND / FOOT / BODY / FALL',
    spec          VARCHAR(60)   NULL COMMENT '规格型号',
    unit          VARCHAR(6)    NOT NULL COMMENT '单位',
    valid_months  INT           NOT NULL COMMENT '使用期限（月）',
    stock         INT           NOT NULL DEFAULT 0 COMMENT '库存',
    min_stock     INT           NOT NULL DEFAULT 0 COMMENT '安全库存',
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    remark        VARCHAR(200)  NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='劳保用品目录';

CREATE TABLE ppe_post (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    name          VARCHAR(40)   NOT NULL COMMENT '岗位名称',
    remark        VARCHAR(200)  NULL,
    status        TINYINT       NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='劳保岗位';

CREATE TABLE ppe_post_user (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    post_id       BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    user_name     VARCHAR(50)   NOT NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_post (tenant_id, post_id),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位人员';

CREATE TABLE ppe_standard (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    post_id       BIGINT        NOT NULL,
    item_id       BIGINT        NOT NULL,
    quantity      INT           NOT NULL DEFAULT 1 COMMENT '每次发放数量',
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_post (tenant_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位劳保发放标准';

CREATE TABLE ppe_issue (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    user_id       BIGINT        NOT NULL COMMENT '领用人',
    user_name     VARCHAR(50)   NOT NULL,
    item_id       BIGINT        NOT NULL,
    item_name     VARCHAR(110)  NOT NULL,
    quantity      INT           NOT NULL,
    issue_date    DATE          NOT NULL,
    expire_date   DATE          NOT NULL COMMENT '发放日期 + 使用期限',
    status        VARCHAR(10)   NOT NULL COMMENT 'IN_USE 在用 / REPLACED 已更换',
    issuer_name   VARCHAR(50)   NULL,
    remark        VARCHAR(200)  NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_user_item (tenant_id, user_id, item_id, status),
    KEY idx_tenant_expire (tenant_id, status, expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='劳保用品发放记录';

-- 菜单：安全管理下面加【劳保用品】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(310,  3,   '劳保用品', 'C', 'T', '/safety/ppe', 'safety/ppe/index', 'ppe:list', 'ppe', 11, NOW(), NOW()),
(3101, 310, '管理和发放劳保用品', 'F', 'T', NULL, NULL, 'ppe:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',310,3101')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('310', menu_ids) = 0;

-- 安全员管理和发放；作业负责人、监护人看自己的
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (310, 3101)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 310
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示用品：滤毒罐库存低于安全库存
INSERT INTO ppe_item (id, tenant_id, name, category, spec, unit, valid_months, stock, min_stock, status, remark, create_time, update_time) VALUES
(9701, 1, '安全帽', 'HEAD', 'ABS 防静电', '顶', 30, 40, 10, 1, '塑料安全帽使用期限一般不超过 30 个月', NOW(), NOW()),
(9702, 1, '防毒面具滤毒罐', 'RESPIRATORY', '3 号 防有机蒸气', '个', 6, 3, 10, 1, '开封后按 6 个月更换，使用中闻到异味立即更换', NOW(), NOW()),
(9703, 1, '防护眼镜', 'EYE_FACE', '防化学飞溅', '副', 12, 25, 5, 1, NULL, NOW(), NOW()),
(9704, 1, '防静电劳保鞋', 'FOOT', '防砸 防静电', '双', 12, 30, 10, 1, NULL, NOW(), NOW()),
(9705, 1, '耐酸碱手套', 'HAND', '丁腈 长袖口', '副', 3, 60, 20, 1, NULL, NOW(), NOW()),
(9706, 1, '全身式安全带', 'FALL', '双大钩', '条', 36, 8, 2, 1, '高处作业用', NOW(), NOW()),
(9751, 2, '安全帽', 'HEAD', '玻璃钢', '顶', 30, 20, 5, 1, NULL, NOW(), NOW());

INSERT INTO ppe_post (id, tenant_id, name, remark, status, create_time, update_time) VALUES
(9711, 1, '罐区操作工', '接触甲醇等有机溶剂', 1, NOW(), NOW()),
(9712, 1, '安全管理人员', NULL, 1, NOW(), NOW());

INSERT INTO ppe_post_user (id, tenant_id, post_id, user_id, user_name, create_time, update_time) VALUES
(9721, 1, 9711, 103, '李四（作业负责人）', NOW(), NOW()),
(9722, 1, 9711, 104, '赵六（监护人）', NOW(), NOW()),
(9723, 1, 9712, 102, '张三（安全员）', NOW(), NOW());

INSERT INTO ppe_standard (id, tenant_id, post_id, item_id, quantity, create_time, update_time) VALUES
(9731, 1, 9711, 9701, 1, NOW(), NOW()),
(9732, 1, 9711, 9702, 2, NOW(), NOW()),
(9733, 1, 9711, 9703, 1, NOW(), NOW()),
(9734, 1, 9711, 9704, 1, NOW(), NOW()),
(9735, 1, 9711, 9705, 2, NOW(), NOW()),
(9736, 1, 9712, 9701, 1, NOW(), NOW()),
(9737, 1, 9712, 9704, 1, NOW(), NOW());

-- 演示发放：李四的滤毒罐过期、劳保鞋快到期、防护眼镜从没发过；赵六的滤毒罐快到期；张三的劳保鞋从没发过
INSERT INTO ppe_issue (id, tenant_id, user_id, user_name, item_id, item_name, quantity, issue_date, expire_date, status, issuer_name, remark, create_time, update_time) VALUES
(9741, 1, 103, '李四（作业负责人）', 9701, '安全帽（ABS 防静电）', 1, DATE_SUB(CURDATE(), INTERVAL 400 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 400 DAY), INTERVAL 30 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9742, 1, 103, '李四（作业负责人）', 9702, '防毒面具滤毒罐（3 号 防有机蒸气）', 2, DATE_SUB(CURDATE(), INTERVAL 200 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 200 DAY), INTERVAL 6 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9743, 1, 103, '李四（作业负责人）', 9704, '防静电劳保鞋（防砸 防静电）', 1, DATE_SUB(CURDATE(), INTERVAL 350 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 350 DAY), INTERVAL 12 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9744, 1, 103, '李四（作业负责人）', 9705, '耐酸碱手套（丁腈 长袖口）', 2, DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 20 DAY), INTERVAL 3 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9745, 1, 104, '赵六（监护人）', 9701, '安全帽（ABS 防静电）', 1, DATE_SUB(CURDATE(), INTERVAL 100 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 100 DAY), INTERVAL 30 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9746, 1, 104, '赵六（监护人）', 9702, '防毒面具滤毒罐（3 号 防有机蒸气）', 2, DATE_SUB(CURDATE(), INTERVAL 170 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 170 DAY), INTERVAL 6 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9747, 1, 104, '赵六（监护人）', 9703, '防护眼镜（防化学飞溅）', 1, DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 60 DAY), INTERVAL 12 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9748, 1, 104, '赵六（监护人）', 9704, '防静电劳保鞋（防砸 防静电）', 1, DATE_SUB(CURDATE(), INTERVAL 60 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 60 DAY), INTERVAL 12 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9749, 1, 104, '赵六（监护人）', 9705, '耐酸碱手套（丁腈 长袖口）', 2, DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 30 DAY), INTERVAL 3 MONTH), 'IN_USE', '张三（安全员）', NULL, NOW(), NOW()),
(9750, 1, 102, '张三（安全员）', 9701, '安全帽（ABS 防静电）', 1, DATE_SUB(CURDATE(), INTERVAL 90 DAY), DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 90 DAY), INTERVAL 30 MONTH), 'IN_USE', '企业管理员', NULL, NOW(), NOW());
