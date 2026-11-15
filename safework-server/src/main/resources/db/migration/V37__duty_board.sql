INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,scope,path,component,perms,icon,sort,create_time,update_time)
VALUES (205,2,'现场作业值班台','C','T','/ticket/duty','ticket/duty/index','ticket:list','file',5,NOW(),NOW());

UPDATE sys_tenant_package SET menu_ids=CONCAT(menu_ids,',205')
WHERE FIND_IN_SET('200',menu_ids)>0 AND FIND_IN_SET('205',menu_ids)=0;

INSERT INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT UUID_SHORT(),tenant_id,role_id,205 FROM sys_role_menu WHERE menu_id=200;