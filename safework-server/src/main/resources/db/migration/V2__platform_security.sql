-- =====================================================================
-- 第七部分：SaaS 平台端（套餐、企业开通）+ 登录安全（登录日志）
-- =====================================================================

-- 1. 套餐：一个套餐包含哪些菜单（功能），企业只能使用套餐内的功能
DROP TABLE IF EXISTS sys_tenant_package;
CREATE TABLE sys_tenant_package (
    id           BIGINT       NOT NULL COMMENT '主键',
    package_name VARCHAR(50)  NOT NULL COMMENT '套餐名称',
    menu_ids     TEXT         NULL COMMENT '包含的菜单 ID，逗号分隔',
    sort         INT          NOT NULL DEFAULT 0,
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    remark       VARCHAR(200) NULL,
    create_time  DATETIME     NULL,
    update_time  DATETIME     NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户套餐（平台级）';

-- 2. 企业表增加：套餐、账号上限、备注
ALTER TABLE sys_tenant
    ADD COLUMN package_id    BIGINT       NULL COMMENT '套餐' AFTER contact_phone,
    ADD COLUMN account_limit INT          NULL COMMENT '账号数量上限，空为不限' AFTER package_id,
    ADD COLUMN remark        VARCHAR(200) NULL COMMENT '备注' AFTER expire_time;

-- 3. 菜单增加作用范围：T 企业端菜单（可放进套餐） P 平台端菜单（只有运营平台能看到）
ALTER TABLE sys_menu
    ADD COLUMN scope CHAR(1) NOT NULL DEFAULT 'T' COMMENT 'T企业菜单 P平台菜单' AFTER menu_type;

-- 4. 登录日志：成功、失败都记录。tenant_id = -1 表示企业编码不存在
DROP TABLE IF EXISTS sys_login_log;
CREATE TABLE sys_login_log (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID，-1 为未知企业',
    tenant_code VARCHAR(32)  NULL COMMENT '登录时填写的企业编码',
    user_id     BIGINT       NULL,
    username    VARCHAR(32)  NULL COMMENT '登录时填写的用户名',
    ip          VARCHAR(64)  NULL,
    browser     VARCHAR(50)  NULL,
    os          VARCHAR(50)  NULL,
    status      TINYINT      NOT NULL COMMENT '1成功 0失败',
    msg         VARCHAR(200) NULL COMMENT '结果说明，如密码错误',
    login_time  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, login_time),
    KEY idx_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

-- 5. 新菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
-- 企业端：系统管理下新增
(1005, 100, '解锁账号', 'F', 'T', NULL,               NULL,                     'system:user:unlock',   NULL,      5, NOW(), NOW()),
(105,  1,   '登录日志', 'C', 'T', '/system/loginlog', 'system/loginlog/index',  'system:loginlog:list', 'login',   6, NOW(), NOW()),
(106,  1,   '在线用户', 'C', 'T', '/system/online',   'system/online/index',    'system:online:list',   'online',  7, NOW(), NOW()),
(1061, 106, '强制下线', 'F', 'T', NULL,               NULL,                     'system:online:kickout', NULL,     1, NOW(), NOW()),
-- 平台端
(5,    0,   '平台管理', 'M', 'P', '/platform',          NULL,                     NULL,                     'platform', 1, NOW(), NOW()),
(500,  5,   '企业管理', 'C', 'P', '/platform/tenant',   'platform/tenant/index',  'platform:tenant:list',   'company',  1, NOW(), NOW()),
(5001, 500, '开通企业', 'F', 'P', NULL,                 NULL,                     'platform:tenant:add',    NULL,       1, NOW(), NOW()),
(5002, 500, '修改企业', 'F', 'P', NULL,                 NULL,                     'platform:tenant:edit',   NULL,       2, NOW(), NOW()),
(501,  5,   '套餐管理', 'C', 'P', '/platform/package',  'platform/package/index', 'platform:package:list',  'package',  2, NOW(), NOW()),
(5011, 501, '编辑套餐', 'F', 'P', NULL,                 NULL,                     'platform:package:edit',  NULL,       1, NOW(), NOW()),
(502,  5,   '登录日志', 'C', 'P', '/platform/loginlog', 'platform/loginlog/index','platform:loginlog:list', 'login',    3, NOW(), NOW());

-- 6. 套餐
INSERT INTO sys_tenant_package (id, package_name, menu_ids, sort, remark, create_time, update_time) VALUES
(1, '旗舰版', '1,100,1001,1002,1003,1004,1005,101,1011,1012,102,103,1031,1032,1033,104,105,106,1061,2,200,2001,2002,2003,3,300,3001,3002,301,3011,3012,4,400,401,4011,4012',
    1, '全部功能，含 AI 助手', NOW(), NOW()),
(2, '标准版', '1,100,1001,1002,1003,1004,1005,101,1011,1012,102,103,1031,1032,1033,104,105,106,1061,2,200,2001,2002,3,300,3001,3002,301,3011,3012',
    2, '作业票 + 受限空间 + 隐患排查，不含 AI', NOW(), NOW()),
(3, '基础版', '1,100,1001,1002,1003,1004,1005,101,1011,1012,102,103,1031,1032,1033,104,105,106,1061,2,200,2001,2002',
    3, '只有作业票', NOW(), NOW());

UPDATE sys_tenant SET package_id = 1, account_limit = 50, expire_time = DATE_ADD(CURDATE(), INTERVAL 1 YEAR) WHERE id = 1;
UPDATE sys_tenant SET package_id = 1, account_limit = 20 WHERE id = 2;

-- 7. 运营平台：租户 ID 固定为 0，企业编码 platform。平台管理员 admin / admin123
INSERT INTO sys_tenant (id, tenant_code, tenant_name, contact_name, status, remark, create_time, update_time) VALUES
(0, 'platform', 'SafeWork 运营平台', '平台运营', 1, '平台自身，不能停用', NOW(), NOW());
INSERT INTO sys_user (id, tenant_id, username, password, nickname, status, create_time, update_time) VALUES
(1, 0, 'admin', '$2a$10$nFCJI7WfF9.FAAF5XR9UTeP0TM0WDkPF0JQgrbNNEqgqIy04QgfDu', '平台超级管理员', 1, NOW(), NOW());
INSERT INTO sys_role (id, tenant_id, role_code, role_name, sort, remark, create_time, update_time) VALUES
(1, 0, 'admin', '平台管理员', 1, '拥有全部平台菜单', NOW(), NOW());
INSERT INTO sys_user_role (id, tenant_id, user_id, role_id) VALUES (100, 0, 1, 1);
