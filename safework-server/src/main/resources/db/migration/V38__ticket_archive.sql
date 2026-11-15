INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time)
VALUES (206, 2, '作业归档台账', 'C', 'T', '/ticket/archive', 'ticket/archive/index', 'ticket:list', 'file', 6, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',206')
WHERE FIND_IN_SET('200', menu_ids) > 0 AND FIND_IN_SET('206', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), tenant_id, role_id, 206 FROM sys_role_menu WHERE menu_id = 200;
