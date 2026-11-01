-- 第二十部分：安全数据大屏（全屏展示，挂在值班室 / 中控室的大屏幕上）

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(8, 0, '安全大屏', 'C', 'T', '/screen', 'screen/index', 'screen:view', 'screen', 6, NOW(), NOW());

-- 有安全管理的套餐都加上；安全员可以看（企业管理员自动拥有套餐里的全部菜单）
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',8')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('8', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, 8
FROM sys_role r
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 8);
