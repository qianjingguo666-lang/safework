CREATE TABLE shift_handover (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    ticket_id BIGINT NOT NULL,
    ticket_no VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(128) NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_name VARCHAR(128) NOT NULL,
    site_condition VARCHAR(500) NOT NULL,
    unfinished VARCHAR(500) NOT NULL,
    recheck VARCHAR(500) NOT NULL,
    accepted_at DATETIME NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX ix_shift_ticket (tenant_id,ticket_id,create_time,id),
    INDEX ix_shift_receiver (tenant_id,receiver_id),
    INDEX ix_shift_sender (tenant_id,sender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_menu (id,parent_id,menu_name,menu_type,scope,path,component,perms,icon,sort,create_time,update_time)
VALUES (207,2,'作业交接班','C','T','/ticket/handover','ticket/handover/index','ticket:list','file',7,NOW(),NOW());

UPDATE sys_tenant_package SET menu_ids=CONCAT(menu_ids,',207')
WHERE FIND_IN_SET('200',menu_ids)>0 AND FIND_IN_SET('207',menu_ids)=0;

INSERT INTO sys_role_menu (id,tenant_id,role_id,menu_id)
SELECT UUID_SHORT(),tenant_id,role_id,207 FROM sys_role_menu WHERE menu_id=200;
