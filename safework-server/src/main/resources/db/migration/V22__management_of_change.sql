-- 第二十七部分：变更管理（MOC：申请 → 风险评估 LEC → 审批 → 实施 → 投用前安全检查 PSSR → 投用；临时变更到期恢复）

CREATE TABLE moc_change (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '企业',
    change_no         VARCHAR(20)   NOT NULL,
    title             VARCHAR(60)   NOT NULL,
    change_type       VARCHAR(12)   NOT NULL COMMENT 'PROCESS / EQUIPMENT / MATERIAL / PERSONNEL / MANAGEMENT',
    temporary         TINYINT       NOT NULL DEFAULT 0 COMMENT '1 临时变更',
    restore_date      DATE          NULL COMMENT '临时变更恢复日期',
    location          VARCHAR(100)  NOT NULL,
    reason            VARCHAR(500)  NOT NULL,
    content           VARCHAR(2000) NOT NULL,
    applicant_id      BIGINT        NOT NULL,
    applicant_name    VARCHAR(50)   NOT NULL,
    status            VARCHAR(12)   NOT NULL COMMENT 'DRAFT / ASSESSING / APPROVING / IMPLEMENTING / PSSR / CLOSED / CANCELLED',
    lec_l             DOUBLE        NULL,
    lec_e             DOUBLE        NULL,
    lec_c             DOUBLE        NULL,
    lec_d             DOUBLE        NULL,
    risk_level        VARCHAR(10)   NULL,
    risk_analysis     VARCHAR(1000) NULL,
    measures          VARCHAR(1000) NULL,
    assessor_name     VARCHAR(50)   NULL,
    assess_time       DATETIME      NULL,
    approvals_needed  INT           NOT NULL DEFAULT 1,
    approval_round    INT           NOT NULL DEFAULT 1,
    implement_note    VARCHAR(500)  NULL,
    implement_time    DATETIME      NULL,
    pssr              TEXT          NULL COMMENT '投用前安全检查结果 JSON',
    pssr_by           VARCHAR(50)   NULL,
    pssr_time         DATETIME      NULL,
    close_time        DATETIME      NULL,
    restored          TINYINT       NOT NULL DEFAULT 0,
    restore_note      VARCHAR(500)  NULL,
    restore_time      DATETIME      NULL,
    version           INT           NOT NULL DEFAULT 0,
    create_time       DATETIME      NULL,
    update_time       DATETIME      NULL,
    deleted           TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_tenant_applicant (tenant_id, applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变更管理';

CREATE TABLE moc_log (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    change_id      BIGINT        NOT NULL,
    action         VARCHAR(16)   NOT NULL,
    round          INT           NOT NULL DEFAULT 1 COMMENT '审批轮次',
    content        VARCHAR(500)  NULL,
    operator_id    BIGINT        NOT NULL,
    operator_name  VARCHAR(50)   NOT NULL,
    create_time    DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_tenant_change (tenant_id, change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='变更操作记录';

-- 菜单：安全管理下面加【变更管理】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(315,  3,   '变更管理', 'C', 'T', '/safety/moc', 'safety/moc/index', 'moc:list', 'moc', 16, NOW(), NOW()),
(3151, 315, '风险评估、投用前检查', 'F', 'T', NULL, NULL, 'moc:edit', NULL, 1, NOW(), NOW()),
(3152, 315, '审批变更', 'F', 'T', NULL, NULL, 'moc:approve', NULL, 2, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',315,3151,3152')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('315', menu_ids) = 0;

-- 安全员评估、检查、审批；作业负责人、监护人能申请。企业管理员（admin）本来就有全部权限，是第二审批人
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (315, 3151, 3152)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 315
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示变更：
-- 1. T-103 储罐改储乙醇：较大风险，需要 2 人审批，张三已批，等企业管理员审批
-- 2. 污水站提升泵临时切换：临时变更已投用，恢复日期已过 2 天还没恢复
-- 3. 罐区操作班长调整：刚提交，等安全员风险评估
INSERT INTO moc_change (id, tenant_id, change_no, title, change_type, temporary, restore_date, location, reason, content, applicant_id, applicant_name, status,
                        lec_l, lec_e, lec_c, lec_d, risk_level, risk_analysis, measures, assessor_name, assess_time, approvals_needed, approval_round,
                        implement_note, implement_time, pssr, pssr_by, pssr_time, close_time, restored, version, create_time, update_time) VALUES
(9301, 1, CONCAT('BG', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 4 DAY), '%Y%m%d'), '-0001'), 'T-103 储罐由储存甲醇改为储存乙醇', 'PROCESS', 0, NULL,
 '罐区 T-103', '甲醇用量下降，新产品线需要乙醇原料', 'T-103（1000 m³ 内浮顶罐）清罐、置换后改储乙醇；更换液位计量程设定；修订罐区操作规程和装卸车作业指导书。',
 103, '李四（作业负责人）', 'APPROVING', 3, 6, 15, 270, 'HIGH',
 '清罐置换涉及受限空间作业和动火作业；乙醇与甲醇物性不同，呼吸阀、阻火器选型需复核；装卸车人员需重新培训。',
 '清罐按受限空间作业票办理；置换后可燃气体检测合格方可动火；复核呼吸阀阻火器；修订操作规程并培训考核后投用。',
 '张三（安全员）', DATE_SUB(NOW(), INTERVAL 3 DAY), 2, 1, NULL, NULL, NULL, NULL, NULL, NULL, 0, 2, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
(9302, 1, CONCAT('BG', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 20 DAY), '%Y%m%d'), '-0001'), '污水站 1# 提升泵临时改用备用泵', 'EQUIPMENT', 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY),
 '污水处理站', '1# 提升泵机械密封泄漏，送外检修', '主泵检修期间临时启用 2# 备用泵，备用泵扬程较低，调整调节池液位联锁设定值。',
 104, '赵六（监护人）', 'CLOSED', 1, 6, 3, 18, 'LOW',
 '备用泵扬程低，调节池可能高液位溢流。', '临时调整液位联锁设定值；每班巡检两次；主泵修好后立即恢复。',
 '张三（安全员）', DATE_SUB(NOW(), INTERVAL 19 DAY), 1, 1, '已切换至 2# 备用泵，联锁设定值已调整', DATE_SUB(NOW(), INTERVAL 18 DAY),
 '[{"item": "变更按批准的方案实施，与设计一致", "ok": true, "note": null}, {"item": "风险评估提出的控制措施已全部落实", "ok": true, "note": null}, {"item": "操作规程、工艺卡片已修订", "ok": true, "note": "临时操作卡已下发"}, {"item": "相关人员已完成培训并考核合格", "ok": true, "note": null}, {"item": "图纸、P&ID、设备台账已更新", "ok": true, "note": null}, {"item": "安全设施、报警联锁已投用并测试正常", "ok": true, "note": "备用泵液位联锁测试正常"}, {"item": "应急预案、处置卡已更新（不涉及的写明“不涉及”）", "ok": true, "note": "不涉及"}]', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), 0, 5, DATE_SUB(NOW(), INTERVAL 20 DAY), NOW()),
(9303, 1, CONCAT('BG', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '-0001'), '罐区操作班长调整', 'PERSONNEL', 0, NULL,
 '罐区', '原班长调岗至新装置', '由罐区操作工王某接任操作班长，原班长调往新装置。', 103, '李四（作业负责人）', 'ASSESSING',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, NULL, NULL, NULL, NULL, NULL, NULL, 0, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW());

INSERT INTO moc_log (id, tenant_id, change_id, action, round, content, operator_id, operator_name, create_time) VALUES
(9311, 1, 9301, 'CREATE', 1, '新建变更申请', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9312, 1, 9301, 'SUBMIT', 1, '提交风险评估', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9313, 1, 9301, 'ASSESS', 1, '风险评估：D = 270，较大风险，需要 2 人审批', 102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9314, 1, 9301, 'APPROVE', 1, '审批通过（1/2）：同意，清罐置换严格按作业票执行', 102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(9321, 1, 9302, 'CREATE', 1, '新建变更申请', 104, '赵六（监护人）', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9322, 1, 9302, 'SUBMIT', 1, '提交风险评估', 104, '赵六（监护人）', DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9323, 1, 9302, 'ASSESS', 1, '风险评估：D = 18，低风险，需要 1 人审批', 102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9324, 1, 9302, 'APPROVE', 1, '审批通过（1/1）', 101, '演示企业管理员', DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9325, 1, 9302, 'IMPLEMENTED', 1, '实施完成：已切换至 2# 备用泵，联锁设定值已调整', 104, '赵六（监护人）', DATE_SUB(NOW(), INTERVAL 18 DAY)),
(9326, 1, 9302, 'PSSR_PASS', 1, '投用前安全检查全部合格，正式投用', 102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 18 DAY)),
(9331, 1, 9303, 'CREATE', 1, '新建变更申请', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9332, 1, 9303, 'SUBMIT', 1, '提交风险评估', 103, '李四（作业负责人）', DATE_SUB(NOW(), INTERVAL 1 DAY));
