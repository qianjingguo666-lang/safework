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
