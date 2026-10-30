-- =====================================================================
-- 第八部分：消息通知（站内信、通知公告）+ 定时任务
-- =====================================================================

-- 1. 站内消息：每个接收人一条，已读状态各自独立
DROP TABLE IF EXISTS sys_message;
CREATE TABLE sys_message (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    user_id     BIGINT       NOT NULL COMMENT '接收人',
    category    VARCHAR(20)  NOT NULL COMMENT 'TODO 待办 / NOTICE 通知 / WARN 预警 / ANNOUNCE 公告',
    biz_type    VARCHAR(20)  NULL COMMENT '业务类型：TICKET / HAZARD / TENANT / NOTICE',
    biz_id      BIGINT       NULL COMMENT '业务 ID，点击消息跳转到对应单据',
    title       VARCHAR(100) NOT NULL,
    content     VARCHAR(500) NULL,
    link        VARCHAR(200) NULL COMMENT '前端跳转地址',
    sender_name VARCHAR(50)  NULL COMMENT '发送人，系统发送为空',
    read_flag   TINYINT      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    read_time   DATETIME     NULL,
    create_time DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_user_read (tenant_id, user_id, read_flag, create_time),
    KEY idx_biz (tenant_id, biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息';

-- 2. 通知公告：企业管理员发布，发布时给全员各生成一条消息
DROP TABLE IF EXISTS sys_notice;
CREATE TABLE sys_notice (
    id             BIGINT       NOT NULL COMMENT '主键',
    tenant_id      BIGINT       NOT NULL COMMENT '租户ID',
    title          VARCHAR(100) NOT NULL,
    content        TEXT         NOT NULL,
    level          VARCHAR(10)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 普通 / IMPORTANT 重要',
    publisher_id   BIGINT       NULL,
    publisher_name VARCHAR(50)  NULL,
    receiver_count INT          NOT NULL DEFAULT 0 COMMENT '发送人数',
    create_time    DATETIME     NULL,
    update_time    DATETIME     NULL,
    deleted        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知公告';

-- 3. 定时任务执行记录（平台级，不分企业）
DROP TABLE IF EXISTS sys_job_log;
CREATE TABLE sys_job_log (
    id            BIGINT        NOT NULL COMMENT '主键',
    job_code      VARCHAR(50)   NOT NULL COMMENT '任务编码',
    job_name      VARCHAR(50)   NOT NULL,
    trigger_type  VARCHAR(10)   NOT NULL COMMENT 'AUTO 定时触发 / MANUAL 手动执行',
    operator_name VARCHAR(50)   NULL COMMENT '手动执行人',
    status        TINYINT       NOT NULL COMMENT '1成功 0失败',
    result        VARCHAR(1000) NULL COMMENT '执行结果摘要或错误信息',
    server        VARCHAR(100)  NULL COMMENT '在哪台服务器执行（多实例部署时区分）',
    start_time    DATETIME      NOT NULL,
    cost_ms       BIGINT        NULL,
    PRIMARY KEY (id),
    KEY idx_job_time (job_code, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务执行记录';

-- 4. 菜单
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(107,  1,   '通知公告', 'C', 'T', '/system/notice', 'system/notice/index', 'system:notice:list', 'notice', 8, NOW(), NOW()),
(1071, 107, '发布公告', 'F', 'T', NULL,             NULL,                  'system:notice:add',  NULL,     1, NOW(), NOW()),
(503,  5,   '定时任务', 'C', 'P', '/platform/job',  'platform/job/index',  'platform:job:list',  'job',    4, NOW(), NOW()),
(5031, 503, '执行任务', 'F', 'P', NULL,             NULL,                  'platform:job:run',   NULL,     1, NOW(), NOW());

-- 5. 所有套餐都包含通知公告
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',107,1071') WHERE FIND_IN_SET('107', menu_ids) = 0;
