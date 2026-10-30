-- 第十一部分：AI 智能体（Function Calling 调用查询工具）

-- 1. 菜单：AI 助手下面加【智能体】，排在最前面
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(402, 4, '智能体', 'C', 'T', '/ai/agent', 'ai/agent/index', 'ai:agent', 'agent', 0, NOW(), NOW());

-- 2. 套餐：含 AI 的旗舰版加上智能体
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',402')
WHERE FIND_IN_SET('400', menu_ids) > 0 AND FIND_IN_SET('402', menu_ids) = 0;

-- 3. 已有企业的安全员、作业负责人、监护人都可以用（能查到什么由各自的数据权限决定）
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, 402
FROM sys_role r
WHERE r.role_code IN ('safety', 'leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 402);
