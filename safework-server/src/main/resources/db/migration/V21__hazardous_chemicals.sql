-- 第二十六部分：危险化学品管理（目录与 MSDS → 存储单元 → 出入库流水 → 重大危险源辨识与分级 GB 18218-2018）

CREATE TABLE chem_item (
    id            BIGINT         NOT NULL COMMENT '主键',
    tenant_id     BIGINT         NOT NULL COMMENT '企业',
    name          VARCHAR(50)    NOT NULL,
    cas           VARCHAR(20)    NOT NULL COMMENT 'CAS 号',
    un_no         VARCHAR(10)    NULL COMMENT 'UN 编号',
    category      VARCHAR(20)    NOT NULL COMMENT '危险类别',
    hazards       VARCHAR(300)   NULL COMMENT '危险特性',
    critical_qty  DECIMAL(12, 3) NULL COMMENT 'GB 18218 临界量（吨），为空不参与辨识',
    beta          DECIMAL(6, 2)  NOT NULL DEFAULT 1 COMMENT '校正系数 β',
    status        TINYINT        NOT NULL DEFAULT 1,
    remark        VARCHAR(200)   NULL,
    create_time   DATETIME       NULL,
    update_time   DATETIME       NULL,
    deleted       TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_cas (tenant_id, cas)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='危险化学品目录';

CREATE TABLE chem_store (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    name          VARCHAR(50)   NOT NULL,
    store_type    VARCHAR(12)   NOT NULL COMMENT 'TANK_AREA 罐区 / WAREHOUSE 库区 / PRODUCTION 生产装置',
    location      VARCHAR(100)  NOT NULL,
    people        INT           NOT NULL DEFAULT 0 COMMENT '单元外 500 m 内常住人数',
    manager_id    BIGINT        NOT NULL,
    manager_name  VARCHAR(50)   NOT NULL,
    status        TINYINT       NOT NULL DEFAULT 1,
    remark        VARCHAR(200)  NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='危化品存储单元';

CREATE TABLE chem_stock (
    id            BIGINT         NOT NULL COMMENT '主键',
    tenant_id     BIGINT         NOT NULL COMMENT '企业',
    store_id      BIGINT         NOT NULL,
    chem_id       BIGINT         NOT NULL,
    qty           DECIMAL(14, 3) NOT NULL DEFAULT 0 COMMENT '当前库存（吨）',
    max_qty       DECIMAL(14, 3) NOT NULL COMMENT '设计最大储量（吨）',
    create_time   DATETIME       NULL,
    update_time   DATETIME       NULL,
    deleted       TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_chem (store_id, chem_id, deleted),
    KEY idx_tenant_chem (tenant_id, chem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单元存放的危化品';

CREATE TABLE chem_flow (
    id             BIGINT         NOT NULL COMMENT '主键',
    tenant_id      BIGINT         NOT NULL COMMENT '企业',
    stock_id       BIGINT         NOT NULL,
    store_id       BIGINT         NOT NULL,
    store_name     VARCHAR(50)    NOT NULL,
    chem_id        BIGINT         NOT NULL,
    chem_name      VARCHAR(50)    NOT NULL,
    flow_type      VARCHAR(8)     NOT NULL COMMENT 'IN 入库 / OUT 出库 / CHECK 盘点',
    qty            DECIMAL(14, 3) NOT NULL COMMENT '数量；盘点时是盘点前后的差',
    balance        DECIMAL(14, 3) NOT NULL COMMENT '变动后的库存',
    purpose        VARCHAR(100)   NULL,
    operator_id    BIGINT         NOT NULL,
    operator_name  VARCHAR(50)    NOT NULL,
    create_time    DATETIME       NULL,
    update_time    DATETIME       NULL,
    deleted        TINYINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, create_time),
    KEY idx_tenant_store (tenant_id, store_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='危化品出入库流水';

-- 菜单：安全管理下面加【危险化学品】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(314,  3,   '危险化学品', 'C', 'T', '/safety/chem', 'safety/chem/index', 'chem:list', 'chem', 15, NOW(), NOW()),
(3141, 314, '管理目录、单元、出入库', 'F', 'T', NULL, NULL, 'chem:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',314,3141')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('314', menu_ids) = 0;

-- 安全员管理；作业负责人、监护人能查目录和 MSDS（单元负责人能办理自己单元的出入库）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (314, 3141)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 314
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示目录：临界量、校正系数参考 GB 18218-2018 表 1 / 表 3（实际使用以标准原文为准）
INSERT INTO chem_item (id, tenant_id, name, cas, un_no, category, hazards, critical_qty, beta, status, remark, create_time, update_time) VALUES
(9201, 1, '甲醇', '67-56-1', '1230', 'FLAMMABLE_LIQUID', '易燃，蒸气与空气可形成爆炸性混合物；有毒，可经皮肤吸收，损害视神经', 500, 1, 1, NULL, NOW(), NOW()),
(9202, 1, '苯', '71-43-2', '1114', 'FLAMMABLE_LIQUID', '易燃；致癌，长期接触损害造血系统', 50, 1, 1, NULL, NOW(), NOW()),
(9203, 1, '汽油', '86290-81-5', '1203', 'FLAMMABLE_LIQUID', '极易燃，蒸气比空气重，能在低处扩散到相当远的地方', 200, 1, 1, NULL, NOW(), NOW()),
(9204, 1, '液氨', '7664-41-7', '1005', 'TOXIC', '有毒，强烈刺激性气味；与空气混合能形成爆炸性混合物', 10, 2, 1, '制冷系统', NOW(), NOW()),
(9205, 1, '液氯', '7782-50-5', '1017', 'TOXIC', '剧毒，强氧化性；泄漏后形成黄绿色气团，沿地面扩散', 5, 4, 1, '水处理用，钢瓶', NOW(), NOW()),
(9206, 1, '液化石油气', '68476-85-7', '1075', 'FLAMMABLE_GAS', '极易燃气体，与空气形成爆炸性混合物，泄漏后积聚在低洼处', 50, 1.5, 1, NULL, NOW(), NOW()),
(9207, 1, '硫化氢', '7783-06-4', '1053', 'TOXIC', '剧毒、易燃；低浓度有臭鸡蛋味，高浓度麻痹嗅觉', 5, 5, 1, '污水处理副产物，不储存', NOW(), NOW()),
(9208, 1, '氢氧化钠', '1310-73-2', '1823', 'CORROSIVE', '强腐蚀性，溶于水放出大量热', NULL, 1, 1, '不在 GB 18218 范围内', NOW(), NOW());

INSERT INTO chem_store (id, tenant_id, name, store_type, location, people, manager_id, manager_name, status, remark, create_time, update_time) VALUES
(9221, 1, '罐区', 'TANK_AREA', '厂区东侧，T-101 ~ T-106 储罐', 120, 103, '李四（作业负责人）', 1, '500 m 内有相邻企业宿舍', NOW(), NOW()),
(9222, 1, '液氨站', 'PRODUCTION', '冷冻站北侧', 20, 104, '赵六（监护人）', 1, NULL, NOW(), NOW()),
(9223, 1, '危化品仓库', 'WAREHOUSE', '厂区西北角甲类仓库', 35, 102, '张三（安全员）', 1, NULL, NOW(), NOW());

-- 罐区：S = 3000/500 + 200/50 + 400/200 = 12，α = 2 → R = 24，三级；汽油超储
-- 液氨站：S = 0.8，不构成；危化品仓库：S = 2/5 + 30/50 = 1.0，恰好构成，R = 1.2 × (4×0.4 + 1.5×0.6) = 3，四级
INSERT INTO chem_stock (id, tenant_id, store_id, chem_id, qty, max_qty, create_time, update_time) VALUES
(9231, 1, 9221, 9201, 2150, 3000, NOW(), NOW()),
(9232, 1, 9221, 9202, 168, 200, NOW(), NOW()),
(9233, 1, 9221, 9203, 412, 400, NOW(), NOW()),
(9234, 1, 9222, 9204, 5.2, 8, NOW(), NOW()),
(9235, 1, 9223, 9205, 1, 2, NOW(), NOW()),
(9236, 1, 9223, 9206, 12, 30, NOW(), NOW()),
(9237, 1, 9223, 9208, 6.5, 20, NOW(), NOW());

INSERT INTO chem_flow (id, tenant_id, stock_id, store_id, store_name, chem_id, chem_name, flow_type, qty, balance, purpose, operator_id, operator_name, create_time, update_time) VALUES
(9251, 1, 9231, 9221, '罐区', 9201, '甲醇', 'IN', 300, 2350, '槽车卸车 3 车', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 6 DAY), NOW()),
(9252, 1, 9231, 9221, '罐区', 9201, '甲醇', 'OUT', 200, 2150, '一车间生产领用', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
(9253, 1, 9233, 9221, '罐区', 9203, '汽油', 'IN', 30, 412, '槽车卸车', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
(9254, 1, 9237, 9223, '危化品仓库', 9208, '氢氧化钠', 'OUT', 1.5, 6.5, '污水站调节 pH', 102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
(9255, 1, 9234, 9222, '液氨站', 9204, '液氨', 'CHECK', -0.3, 5.2, '月度盘点，液位计读数', 104, '赵六（监护人）', DATE_SUB(NOW(), INTERVAL 4 DAY), NOW());
