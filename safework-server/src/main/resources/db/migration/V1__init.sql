-- V1：基础表结构和演示数据（租户、用户、RBAC、作业票、部门、操作日志、受限空间、隐患、AI 知识库）
-- 数据库版本由 Flyway 管理：后端启动时自动按版本号顺序执行还没执行过的脚本，执行记录在 flyway_schema_history 表。
-- 已经执行过的脚本不能再修改（Flyway 会校验文件指纹），要改表结构就新增一个更高版本的脚本。

-- 租户表（平台级，不做租户隔离）
DROP TABLE IF EXISTS sys_tenant;
CREATE TABLE sys_tenant (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_code   VARCHAR(32)  NOT NULL COMMENT '企业编码（登录用）',
    tenant_name   VARCHAR(100) NOT NULL COMMENT '企业名称',
    contact_name  VARCHAR(50)  NULL COMMENT '联系人',
    contact_phone VARCHAR(20)  NULL COMMENT '联系电话',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    expire_time   DATETIME     NULL COMMENT '到期时间，空为不限',
    create_time   DATETIME     NULL,
    update_time   DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户';

-- 用户表（按 tenant_id 隔离）
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    username    VARCHAR(32)  NOT NULL COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密文',
    nickname    VARCHAR(50)  NULL COMMENT '昵称',
    phone       VARCHAR(20)  NULL COMMENT '手机号',
    dept_id     BIGINT       NULL COMMENT '所属部门',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 演示数据：两个企业，用来验证数据隔离。所有账号密码都是 admin123
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, status, create_time, update_time) VALUES
(1, 'demo', '演示化工有限公司', '张经理', 1, NOW(), NOW()),
(2, 'test', '测试冶金有限公司', '李经理', 1, NOW(), NOW());

INSERT INTO sys_user (id, tenant_id, username, password, nickname, status, create_time, update_time) VALUES
(101, 1, 'admin',   '$2a$10$nFCJI7WfF9.FAAF5XR9UTeP0TM0WDkPF0JQgrbNNEqgqIy04QgfDu', '演示企业管理员', 1, NOW(), NOW()),
(102, 1, 'zhangsan','$2a$10$nFCJI7WfF9.FAAF5XR9UTeP0TM0WDkPF0JQgrbNNEqgqIy04QgfDu', '张三（安全员）', 1, NOW(), NOW()),
(103, 1, 'lisi',    '$2a$10$nFCJI7WfF9.FAAF5XR9UTeP0TM0WDkPF0JQgrbNNEqgqIy04QgfDu', '李四（作业负责人）', 1, NOW(), NOW()),
(104, 1, 'zhaoliu', '$2a$10$nFCJI7WfF9.FAAF5XR9UTeP0TM0WDkPF0JQgrbNNEqgqIy04QgfDu', '赵六（监护人）', 1, NOW(), NOW()),
(201, 2, 'admin',   '$2a$10$nFCJI7WfF9.FAAF5XR9UTeP0TM0WDkPF0JQgrbNNEqgqIy04QgfDu', '测试企业管理员', 1, NOW(), NOW()),
(202, 2, 'wangwu',  '$2a$10$nFCJI7WfF9.FAAF5XR9UTeP0TM0WDkPF0JQgrbNNEqgqIy04QgfDu', '王五（监护人）', 1, NOW(), NOW());

-- =====================================================================
-- 第二部分：RBAC 权限（角色、菜单、关联表）
-- =====================================================================

-- 角色表（按 tenant_id 隔离，每个企业有自己的角色）
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    role_code   VARCHAR(32)  NOT NULL COMMENT '角色编码，admin 为企业管理员',
    role_name   VARCHAR(50)  NOT NULL COMMENT '角色名称',
    sort        INT          NOT NULL DEFAULT 99 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    remark      VARCHAR(200) NULL COMMENT '备注',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_code (tenant_id, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 菜单表（平台级，所有企业共用一套菜单）
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id          BIGINT       NOT NULL COMMENT '主键',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID，0 为顶级',
    menu_name   VARCHAR(50)  NOT NULL COMMENT '菜单名称',
    menu_type   CHAR(1)      NOT NULL COMMENT 'M目录 C页面 F按钮',
    path        VARCHAR(200) NULL COMMENT '路由地址',
    component   VARCHAR(200) NULL COMMENT '前端组件路径',
    perms       VARCHAR(100) NULL COMMENT '权限码',
    icon        VARCHAR(50)  NULL COMMENT '图标',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限';

-- 用户-角色关联
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id        BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    user_id   BIGINT NOT NULL COMMENT '用户ID',
    role_id   BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (tenant_id, user_id, role_id),
    KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 角色-菜单关联
DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
    id        BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    role_id   BIGINT NOT NULL COMMENT '角色ID',
    menu_id   BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_menu (tenant_id, role_id, menu_id),
    KEY idx_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联';

-- 菜单数据：系统管理 + 作业管理（作业票模块下一阶段实现，这里先把权限码定好）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, sort, create_time, update_time) VALUES
(1,    0,   '系统管理', 'M', '/system',       NULL,                 NULL,               'setting',  90, NOW(), NOW()),
(100,  1,   '用户管理', 'C', '/system/user',  'system/user/index',  'system:user:list', 'user',     1,  NOW(), NOW()),
(1001, 100, '新增用户', 'F', NULL,            NULL,                 'system:user:add',  NULL,       1,  NOW(), NOW()),
(1002, 100, '分配角色', 'F', NULL,            NULL,                 'system:user:role', NULL,       2,  NOW(), NOW()),
(101,  1,   '角色管理', 'C', '/system/role',  'system/role/index',  'system:role:list', 'team',     2,  NOW(), NOW()),
(1011, 101, '新增角色', 'F', NULL,            NULL,                 'system:role:add',  NULL,       1,  NOW(), NOW()),
(1012, 101, '分配权限', 'F', NULL,            NULL,                 'system:role:menu', NULL,       2,  NOW(), NOW()),
(102,  1,   '菜单管理', 'C', '/system/menu',  'system/menu/index',  'system:menu:list', 'menu',     3,  NOW(), NOW()),
(2,    0,   '作业管理', 'M', '/ticket',       NULL,                 NULL,               'safety',   10, NOW(), NOW()),
(200,  2,   '作业票',   'C', '/ticket/list',  'ticket/list/index',  'ticket:list',      'file',     1,  NOW(), NOW()),
(2001, 200, '申请作业', 'F', NULL,            NULL,                 'ticket:add',       NULL,       1,  NOW(), NOW()),
(2002, 200, '审批作业', 'F', NULL,            NULL,                 'ticket:approve',   NULL,       2,  NOW(), NOW()),
-- 安全管理
(3,    0,   '安全管理', 'M', '/safety',       NULL,                 NULL,               'shield',   20, NOW(), NOW()),
(300,  3,   '受限空间', 'C', '/safety/space', 'safety/space/index', 'safety:space:list', 'space',   1,  NOW(), NOW()),
(3001, 300, '新增空间', 'F', NULL,            NULL,                 'safety:space:add',  NULL,      1,  NOW(), NOW()),
(3002, 300, '修改空间', 'F', NULL,            NULL,                 'safety:space:edit', NULL,      2,  NOW(), NOW()),
(301,  3,   '隐患排查', 'C', '/safety/hazard','safety/hazard/index','safety:hazard:list','hazard',  2,  NOW(), NOW()),
(3011, 301, '上报隐患', 'F', NULL,            NULL,                 'safety:hazard:report', NULL,   1,  NOW(), NOW()),
(3012, 301, '指派验收', 'F', NULL,            NULL,                 'safety:hazard:handle', NULL,   2,  NOW(), NOW()),
-- 系统管理补充
(1003, 100, '修改用户', 'F', NULL,            NULL,                 'system:user:edit', NULL,       3,  NOW(), NOW()),
(1004, 100, '重置密码', 'F', NULL,            NULL,                 'system:user:reset', NULL,      4,  NOW(), NOW()),
(103,  1,   '部门管理', 'C', '/system/dept',  'system/dept/index',  'system:dept:list', 'dept',     4,  NOW(), NOW()),
(1031, 103, '新增部门', 'F', NULL,            NULL,                 'system:dept:add',  NULL,       1,  NOW(), NOW()),
(1032, 103, '修改部门', 'F', NULL,            NULL,                 'system:dept:edit', NULL,       2,  NOW(), NOW()),
(1033, 103, '删除部门', 'F', NULL,            NULL,                 'system:dept:remove', NULL,     3,  NOW(), NOW()),
(104,  1,   '操作日志', 'C', '/system/log',   'system/log/index',   'system:log:list',  'log',      5,  NOW(), NOW()),
-- AI 助手
(4,    0,   'AI 助手',  'M', '/ai',           NULL,                 NULL,               'ai',       30, NOW(), NOW()),
(400,  4,   '安全问答', 'C', '/ai/chat',      'ai/chat/index',      'ai:chat',          'chat',     1,  NOW(), NOW()),
(401,  4,   '知识库',   'C', '/ai/knowledge', 'ai/knowledge/index', 'ai:knowledge:list','book',     2,  NOW(), NOW()),
(4011, 401, '上传文档', 'F', NULL,            NULL,                 'ai:knowledge:upload', NULL,    1,  NOW(), NOW()),
(4012, 401, '删除文档', 'F', NULL,            NULL,                 'ai:knowledge:remove', NULL,    2,  NOW(), NOW()),
(2003, 200, 'AI 预审',  'F', NULL,            NULL,                 'ai:ticket:review', NULL,       3,  NOW(), NOW());

-- 每个企业预置 4 个角色，正好对应后面作业票审批流的各个环节
INSERT INTO sys_role (id, tenant_id, role_code, role_name, sort, remark, create_time, update_time) VALUES
(11, 1, 'admin',    '企业管理员', 1, '拥有全部权限',       NOW(), NOW()),
(12, 1, 'safety',   '安全员',     2, '审批作业票',         NOW(), NOW()),
(13, 1, 'leader',   '作业负责人', 3, '申请作业票',         NOW(), NOW()),
(14, 1, 'guardian', '监护人',     4, '现场监护，查看作业票', NOW(), NOW()),
(21, 2, 'admin',    '企业管理员', 1, '拥有全部权限',       NOW(), NOW()),
(22, 2, 'safety',   '安全员',     2, '审批作业票',         NOW(), NOW()),
(23, 2, 'leader',   '作业负责人', 3, '申请作业票',         NOW(), NOW()),
(24, 2, 'guardian', '监护人',     4, '现场监护，查看作业票', NOW(), NOW());

INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES
(1, 1, 101, 11),  -- 演示化工 admin    -> 企业管理员
(2, 1, 102, 12),  -- 演示化工 zhangsan -> 安全员
(3, 1, 103, 13),  -- 演示化工 lisi     -> 作业负责人
(6, 1, 104, 14),  -- 演示化工 zhaoliu  -> 监护人
(4, 2, 201, 21),  -- 测试冶金 admin    -> 企业管理员
(5, 2, 202, 24);  -- 测试冶金 wangwu   -> 监护人

-- 企业管理员不需要配置（代码里直接拥有套餐内的全部菜单）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id) VALUES
-- 演示化工：安全员 = 查看 + 审批
(1,  1, 12, 2), (2,  1, 12, 200), (3,  1, 12, 2002),
-- 演示化工：作业负责人 = 查看 + 申请
(4,  1, 13, 2), (5,  1, 13, 200), (6,  1, 13, 2001),
-- 演示化工：监护人 = 只能查看
(7,  1, 14, 2), (8,  1, 14, 200),
-- 测试冶金：同上
(11, 2, 22, 2), (12, 2, 22, 200), (13, 2, 22, 2002),
(14, 2, 23, 2), (15, 2, 23, 200), (16, 2, 23, 2001),
(17, 2, 24, 2), (18, 2, 24, 200),
-- 安全管理：安全员 = 查看空间 + 上报、指派、验收隐患
(21, 1, 12, 3), (22, 1, 12, 300), (23, 1, 12, 301), (24, 1, 12, 3011), (25, 1, 12, 3012),
-- 安全管理：作业负责人、监护人 = 查看空间 + 上报隐患
(26, 1, 13, 3), (27, 1, 13, 300), (28, 1, 13, 301), (29, 1, 13, 3011),
(30, 1, 14, 3), (31, 1, 14, 300), (32, 1, 14, 301), (33, 1, 14, 3011),
(41, 2, 22, 3), (42, 2, 22, 300), (43, 2, 22, 301), (44, 2, 22, 3011), (45, 2, 22, 3012),
(46, 2, 23, 3), (47, 2, 23, 300), (48, 2, 23, 301), (49, 2, 23, 3011),
(50, 2, 24, 3), (51, 2, 24, 300), (52, 2, 24, 301), (53, 2, 24, 3011),
-- AI 助手：所有人都能问答；安全员还能管理知识库、使用作业票 AI 预审
(61, 1, 12, 4), (62, 1, 12, 400), (63, 1, 12, 401), (64, 1, 12, 4011), (65, 1, 12, 4012), (66, 1, 12, 2003),
(67, 1, 13, 4), (68, 1, 13, 400),
(69, 1, 14, 4), (70, 1, 14, 400),
(71, 2, 22, 4), (72, 2, 22, 400), (73, 2, 22, 401), (74, 2, 22, 4011), (75, 2, 22, 4012), (76, 2, 22, 2003),
(77, 2, 23, 4), (78, 2, 23, 400),
(79, 2, 24, 4), (80, 2, 24, 400);

-- =====================================================================
-- 第三部分：作业票、审批流
-- =====================================================================

DROP TABLE IF EXISTS work_ticket;
CREATE TABLE work_ticket (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    ticket_no         VARCHAR(32)   NOT NULL COMMENT '作业票编号',
    ticket_type       VARCHAR(20)   NOT NULL COMMENT 'CONFINED_SPACE受限空间 HOT_WORK动火 HEIGHT高处',
    space_id          BIGINT        NULL COMMENT '关联受限空间（受限空间作业必填）',
    space_name        VARCHAR(100)  NULL,
    title             VARCHAR(100)  NOT NULL COMMENT '作业名称',
    location          VARCHAR(200)  NOT NULL COMMENT '作业地点',
    work_content      VARCHAR(1000) NULL COMMENT '作业内容',
    safety_measures   VARCHAR(2000) NULL COMMENT '安全措施',
    plan_start_time   DATETIME      NOT NULL COMMENT '计划开始时间',
    plan_end_time     DATETIME      NOT NULL COMMENT '计划结束时间',
    actual_start_time DATETIME      NULL COMMENT '实际开始时间',
    actual_end_time   DATETIME      NULL COMMENT '实际结束时间',
    applicant_id      BIGINT        NOT NULL COMMENT '申请人（作业负责人）',
    applicant_name    VARCHAR(50)   NULL,
    guardian_id       BIGINT        NOT NULL COMMENT '监护人',
    guardian_name     VARCHAR(50)   NULL,
    approver_id       BIGINT        NULL COMMENT '审批人',
    approver_name     VARCHAR(50)   NULL,
    gas_oxygen        DECIMAL(5,2)  NULL COMMENT '氧含量 %VOL',
    gas_lel           DECIMAL(5,2)  NULL COMMENT '可燃气体 %LEL',
    gas_h2s           DECIMAL(6,2)  NULL COMMENT '硫化氢 mg/m3',
    gas_check_time    DATETIME      NULL COMMENT '气体检测时间',
    status            VARCHAR(20)   NOT NULL COMMENT '状态',
    version           INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_ticket_no (tenant_id, ticket_no),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_applicant (tenant_id, applicant_id),
    KEY idx_guardian (tenant_id, guardian_id),
    KEY idx_space (tenant_id, space_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业票';

DROP TABLE IF EXISTS work_ticket_log;
CREATE TABLE work_ticket_log (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '租户ID',
    ticket_id     BIGINT       NOT NULL COMMENT '作业票ID',
    action        VARCHAR(20)  NOT NULL COMMENT '操作',
    from_status   VARCHAR(20)  NULL COMMENT '操作前状态',
    to_status     VARCHAR(20)  NULL COMMENT '操作后状态',
    operator_id   BIGINT       NOT NULL COMMENT '操作人',
    operator_name VARCHAR(50)  NULL,
    comment       VARCHAR(500) NULL COMMENT '意见',
    create_time   DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_ticket (tenant_id, ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业票操作记录';

-- =====================================================================
-- 第四部分：部门、操作日志
-- =====================================================================

DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级部门，0 为顶级',
    dept_name   VARCHAR(50)  NOT NULL COMMENT '部门名称',
    leader      VARCHAR(50)  NULL COMMENT '负责人',
    phone       VARCHAR(20)  NULL COMMENT '联系电话',
    sort        INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_parent (tenant_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

INSERT INTO sys_dept (id, tenant_id, parent_id, dept_name, leader, sort, create_time, update_time) VALUES
(1101, 1, 0,    '生产部',     '王厂长', 1, NOW(), NOW()),
(1102, 1, 1101, '储运车间',   '李四',   1, NOW(), NOW()),
(1103, 1, 1101, '合成车间',   NULL,     2, NOW(), NOW()),
(1104, 1, 0,    '安全环保部', '张三',   2, NOW(), NOW()),
(1105, 1, 0,    '设备部',     NULL,     3, NOW(), NOW()),
(2101, 2, 0,    '炼钢车间',   NULL,     1, NOW(), NOW()),
(2102, 2, 0,    '安全部',     NULL,     2, NOW(), NOW());

UPDATE sys_user SET dept_id = 1104 WHERE id IN (101, 102);
UPDATE sys_user SET dept_id = 1102 WHERE id IN (103, 104);
UPDATE sys_user SET dept_id = 2102 WHERE id = 201;
UPDATE sys_user SET dept_id = 2101 WHERE id = 202;

DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    module         VARCHAR(50)   NULL COMMENT '模块',
    action         VARCHAR(50)   NULL COMMENT '操作',
    operator_id    BIGINT        NULL,
    operator_name  VARCHAR(50)   NULL,
    request_method VARCHAR(10)   NULL,
    request_uri    VARCHAR(255)  NULL,
    method         VARCHAR(255)  NULL COMMENT 'Java 方法',
    request_params TEXT          NULL COMMENT '请求参数（已脱敏）',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_msg      VARCHAR(1000) NULL,
    cost_ms        BIGINT        NULL COMMENT '耗时毫秒',
    ip             VARCHAR(64)   NULL,
    create_time    DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- =====================================================================
-- 第五部分：受限空间台账、隐患排查
-- =====================================================================

DROP TABLE IF EXISTS confined_space;
CREATE TABLE confined_space (
    id               BIGINT       NOT NULL COMMENT '主键',
    tenant_id        BIGINT       NOT NULL COMMENT '租户ID',
    space_code       VARCHAR(32)  NOT NULL COMMENT '空间编号',
    space_name       VARCHAR(100) NOT NULL COMMENT '空间名称',
    space_type       VARCHAR(20)  NOT NULL COMMENT '类型：储罐、反应釜、污水池、地下有限空间、管道、其他',
    location         VARCHAR(200) NOT NULL COMMENT '位置',
    dept_id          BIGINT       NULL COMMENT '所属部门',
    responsible_id   BIGINT       NULL COMMENT '责任人',
    responsible_name VARCHAR(50)  NULL,
    medium           VARCHAR(200) NULL COMMENT '存在的有毒有害、易燃易爆介质',
    risk_level       VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH高 MEDIUM中 LOW低',
    qr_token         VARCHAR(32)  NOT NULL COMMENT '二维码令牌（扫码匿名访问用，不可猜测）',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark           VARCHAR(500) NULL,
    create_time      DATETIME     NULL,
    update_time      DATETIME     NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, space_code),
    UNIQUE KEY uk_qr_token (qr_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='受限空间台账';

INSERT INTO confined_space (id, tenant_id, space_code, space_name, space_type, location, dept_id, responsible_id, responsible_name, medium, risk_level, qr_token, create_time, update_time) VALUES
(1001, 1, 'CS-001', '1# 原料储罐 T-101', '储罐',         '罐区东侧',     1102, 103,  '李四（作业负责人）', '甲醇',             'HIGH',   'a3f1c9e27b5d4f6c8e0a1b2c3d4e5f60', NOW(), NOW()),
(1002, 1, 'CS-002', '2# 原料储罐 T-102', '储罐',         '罐区东侧',     1102, 103,  '李四（作业负责人）', '甲醇',             'HIGH',   'b4e2d0f38c6e5a7d9f1b2c3d4e5f6a71', NOW(), NOW()),
(1003, 1, 'CS-003', 'R-201 合成反应釜',  '反应釜',       '合成车间二楼', 1103, NULL, NULL,                 '氢气、一氧化碳',   'HIGH',   'c5f3e1a49d7f6b8e0a2c3d4e5f6a7b82', NOW(), NOW()),
(1004, 1, 'CS-004', '污水处理调节池',    '污水池',       '污水处理站',   1105, NULL, NULL,                 '硫化氢、沼气',     'MEDIUM', 'd6a4f2b50e8a7c9f1b3d4e5f6a7b8c93', NOW(), NOW()),
(1005, 1, 'CS-005', '北区地下电缆沟',    '地下有限空间', '厂区北路',     1105, NULL, NULL,                 '可能缺氧',         'LOW',    'e7b5a3c61f9b8d0a2c4e5f6a7b8c9da4', NOW(), NOW()),
(2001, 2, 'CS-001', '1# 转炉烟道',       '管道',         '炼钢车间',     2101, NULL, NULL,                 '一氧化碳',         'HIGH',   'f8c6b4d72a0c9e1b3d5f6a7b8c9d0eb5', NOW(), NOW());

DROP TABLE IF EXISTS hazard;
CREATE TABLE hazard (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '租户ID',
    hazard_no       VARCHAR(32)   NOT NULL COMMENT '隐患编号',
    title           VARCHAR(100)  NOT NULL COMMENT '隐患标题',
    description     VARCHAR(1000) NULL COMMENT '隐患描述',
    category        VARCHAR(20)   NOT NULL COMMENT '类别：设备设施、作业环境、人员行为、安全管理',
    level           VARCHAR(10)   NOT NULL COMMENT 'GENERAL一般 MAJOR重大',
    location        VARCHAR(200)  NOT NULL COMMENT '位置',
    space_id        BIGINT        NULL COMMENT '关联受限空间',
    space_name      VARCHAR(100)  NULL,
    reporter_id     BIGINT        NOT NULL,
    reporter_name   VARCHAR(50)   NULL,
    rectifier_id    BIGINT        NULL COMMENT '整改责任人',
    rectifier_name  VARCHAR(50)   NULL,
    deadline        DATETIME      NULL COMMENT '整改期限',
    rectify_measure VARCHAR(1000) NULL COMMENT '整改措施',
    rectify_time    DATETIME      NULL COMMENT '整改完成时间',
    verifier_id     BIGINT        NULL COMMENT '验收人',
    verifier_name   VARCHAR(50)   NULL,
    verify_time     DATETIME      NULL,
    status          VARCHAR(20)   NOT NULL COMMENT '状态',
    version         INT           NOT NULL DEFAULT 0,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_no (tenant_id, hazard_no),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_rectifier (tenant_id, rectifier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='隐患';

DROP TABLE IF EXISTS hazard_log;
CREATE TABLE hazard_log (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '租户ID',
    hazard_id     BIGINT       NOT NULL,
    action        VARCHAR(20)  NOT NULL,
    from_status   VARCHAR(20)  NULL,
    to_status     VARCHAR(20)  NULL,
    operator_id   BIGINT       NOT NULL,
    operator_name VARCHAR(50)  NULL,
    comment       VARCHAR(500) NULL,
    create_time   DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_hazard (tenant_id, hazard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='隐患处理记录';

-- 演示数据：一条待指派，一条整改中且已逾期
INSERT INTO hazard (id, tenant_id, hazard_no, title, description, category, level, location, space_id, space_name, reporter_id, reporter_name, rectifier_id, rectifier_name, deadline, status, create_time, update_time) VALUES
(1, 1, 'YH20261020-0001', '罐区消防通道被杂物占用', '罐区东侧消防通道堆放了废旧托盘，影响消防车通行', '作业环境', 'GENERAL', '罐区东侧', NULL, NULL,
    104, '赵六（监护人）', NULL, NULL, NULL, 'REPORTED', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 1, 'YH20261020-0002', 'R-201 反应釜人孔垫片老化渗漏', '人孔法兰处有轻微渗漏痕迹，垫片老化', '设备设施', 'MAJOR', '合成车间二楼', 1003, 'R-201 合成反应釜',
    103, '李四（作业负责人）', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 1 DAY), 'RECTIFYING', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY));

INSERT INTO hazard_log (id, tenant_id, hazard_id, action, from_status, to_status, operator_id, operator_name, comment, create_time) VALUES
(1, 1, 1, 'REPORT', NULL, 'REPORTED', 104, '赵六（监护人）', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 1, 2, 'REPORT', NULL, 'REPORTED', 103, '李四（作业负责人）', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY)),
(3, 1, 2, 'ASSIGN', 'REPORTED', 'RECTIFYING', 102, '张三（安全员）', '更换垫片，整改前办理受限空间作业票', DATE_SUB(NOW(), INTERVAL 4 DAY));

-- =====================================================================
-- 第六部分：AI 助手（知识库 RAG、问答记录）
-- =====================================================================

DROP TABLE IF EXISTS ai_document;
CREATE TABLE ai_document (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '租户ID',
    title         VARCHAR(200)  NOT NULL COMMENT '文档标题',
    file_name     VARCHAR(255)  NOT NULL COMMENT '原始文件名',
    file_type     VARCHAR(10)   NOT NULL COMMENT 'pdf / txt / md',
    file_size     BIGINT        NOT NULL DEFAULT 0 COMMENT '字节数',
    char_count    INT           NOT NULL DEFAULT 0 COMMENT '提取出的字数',
    chunk_count   INT           NOT NULL DEFAULT 0 COMMENT '切分片段数',
    embedded      TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已生成向量',
    status        VARCHAR(20)   NOT NULL COMMENT 'READY 可用 / FAILED 失败',
    error_msg     VARCHAR(500)  NULL,
    uploader_id   BIGINT        NULL,
    uploader_name VARCHAR(50)   NULL,
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

DROP TABLE IF EXISTS ai_chunk;
CREATE TABLE ai_chunk (
    id          BIGINT      NOT NULL COMMENT '主键',
    tenant_id   BIGINT      NOT NULL COMMENT '租户ID',
    document_id BIGINT      NOT NULL COMMENT '所属文档',
    chunk_index INT         NOT NULL COMMENT '片段序号',
    page_no     INT         NULL COMMENT '所在页码（PDF）',
    content     TEXT        NOT NULL COMMENT '片段原文',
    embedding   MEDIUMTEXT  NULL COMMENT '向量（float32 小端序后 Base64 编码）',
    create_time DATETIME    NULL,
    PRIMARY KEY (id),
    KEY idx_tenant_doc (tenant_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库片段';

DROP TABLE IF EXISTS ai_chat_log;
CREATE TABLE ai_chat_log (
    id           BIGINT        NOT NULL COMMENT '主键',
    tenant_id    BIGINT        NOT NULL COMMENT '租户ID',
    biz_type     VARCHAR(20)   NOT NULL COMMENT 'CHAT 问答 / REVIEW 作业票预审',
    user_id      BIGINT        NULL,
    user_name    VARCHAR(50)   NULL,
    question     VARCHAR(2000) NOT NULL,
    answer       TEXT          NULL,
    sources      TEXT          NULL COMMENT '引用的片段（JSON）',
    hit          TINYINT       NOT NULL DEFAULT 1 COMMENT '知识库是否命中，未命中的问题可以用来补充知识库',
    model        VARCHAR(50)   NULL,
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_msg    VARCHAR(500)  NULL,
    cost_ms      BIGINT        NULL,
    create_time  DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 问答记录';
