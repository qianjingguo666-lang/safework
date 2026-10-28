SET NAMES utf8mb4;
-- 智能安全作业管理平台 初始化脚本（第一阶段：租户 + 用户）
CREATE DATABASE IF NOT EXISTS safework DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE safework;

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
(2002, 200, '审批作业', 'F', NULL,            NULL,                 'ticket:approve',   NULL,       2,  NOW(), NOW());

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
(4, 2, 201, 21),  -- 测试冶金 admin    -> 企业管理员
(5, 2, 202, 24);  -- 测试冶金 wangwu   -> 监护人

-- 企业管理员不需要配置（代码里直接给 * 全部权限）
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
(17, 2, 24, 2), (18, 2, 24, 200);
