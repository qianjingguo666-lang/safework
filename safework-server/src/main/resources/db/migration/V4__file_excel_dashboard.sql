-- =====================================================================
-- 第九部分：附件（现场照片）+ Excel 导入导出 + 数据看板
-- =====================================================================

-- 1. 附件：挂在业务单据上（隐患现场照片、整改照片、作业票附件），访问权限跟随单据
DROP TABLE IF EXISTS sys_file;
CREATE TABLE sys_file (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '租户ID',
    biz_type      VARCHAR(20)  NOT NULL COMMENT 'HAZARD 隐患 / TICKET 作业票',
    biz_id        BIGINT       NOT NULL COMMENT '业务单据 ID',
    tag           VARCHAR(20)  NOT NULL COMMENT '用途：REPORT 隐患现场照片 / RECTIFY 整改后照片 / ATTACH 作业票附件',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    ext           VARCHAR(10)  NOT NULL COMMENT '扩展名（按文件内容识别，不信任上传的文件名）',
    content_type  VARCHAR(100) NOT NULL,
    size          BIGINT       NOT NULL COMMENT '字节数',
    storage_key   VARCHAR(200) NOT NULL COMMENT '存储路径：租户/年月/随机名，不包含原始文件名',
    uploader_id   BIGINT       NULL,
    uploader_name VARCHAR(50)  NULL,
    create_time   DATETIME     NULL,
    update_time   DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_biz (tenant_id, biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件';

-- 2. 菜单：数据看板 + 导入导出按钮
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(6,    0,   '数据看板', 'C', 'T', '/dashboard', 'dashboard/index', 'dashboard:view', 'chart', 5, NOW(), NOW()),
(2004, 200, '导出作业票', 'F', 'T', NULL, NULL, 'ticket:export', NULL, 4, NOW(), NOW()),
(3003, 300, '导入导出', 'F', 'T', NULL, NULL, 'safety:space:import', NULL, 3, NOW(), NOW()),
(3013, 301, '导出隐患', 'F', 'T', NULL, NULL, 'safety:hazard:export', NULL, 3, NOW(), NOW());

-- 3. 套餐：旗舰版、标准版加上全部新功能；基础版只有作业票，加看板和导出作业票
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',6,2004,3003,3013') WHERE id IN (1, 2) AND FIND_IN_SET('6', menu_ids) = 0;
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',6,2004') WHERE id = 3 AND FIND_IN_SET('6', menu_ids) = 0;

-- 4. 所有企业的安全员角色：可以看看板、导入导出（实际能不能用还要看企业套餐）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (6, 2004, 3003, 3013)
WHERE r.role_code = 'safety' AND r.deleted = 0;
