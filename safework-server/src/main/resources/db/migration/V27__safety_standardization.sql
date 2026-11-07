-- 第三十二部分：安全生产标准化自评（GB/T 33000 的 8 个一级要素、27 个考评项目，系统取证给建议扣分，空项不计分，得出等级）

CREATE TABLE std_assessment (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    title         VARCHAR(60)   NOT NULL,
    year          INT           NOT NULL,
    status        VARCHAR(8)    NOT NULL COMMENT 'DRAFT 自评中 / DONE 已完成',
    leader_id     BIGINT        NOT NULL COMMENT '自评组长',
    leader_name   VARCHAR(50)   NOT NULL,
    members       VARCHAR(200)  NULL COMMENT '自评组成员',
    start_date    DATE          NOT NULL,
    finish_date   DATE          NULL,
    score         DOUBLE        NULL COMMENT '得分（百分制）',
    grade         VARCHAR(8)    NULL COMMENT '一级 / 二级 / 三级 / 未达标',
    perf_dead     INT           NULL COMMENT '完成时近一年死亡人数',
    perf_serious  INT           NULL COMMENT '完成时近一年重伤人数',
    summary       VARCHAR(500)  NULL COMMENT '自评结论',
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status, finish_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全生产标准化自评';

CREATE TABLE std_item (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    assessment_id   BIGINT        NOT NULL,
    code            VARCHAR(8)    NOT NULL COMMENT '编号，如 5.3',
    element         INT           NOT NULL COMMENT '一级要素序号 1~8',
    element_name    VARCHAR(30)   NOT NULL,
    title           VARCHAR(30)   NOT NULL,
    content         VARCHAR(200)  NOT NULL COMMENT '考评内容',
    standard_score  INT           NOT NULL COMMENT '标准分值',
    method          VARCHAR(200)  NOT NULL COMMENT '考评办法',
    auto            TINYINT       NOT NULL DEFAULT 0 COMMENT '1 系统自动取证',
    na              TINYINT       NOT NULL DEFAULT 0 COMMENT '1 空项',
    deduct          DOUBLE        NOT NULL DEFAULT 0 COMMENT '扣分',
    suggested       DOUBLE        NULL COMMENT '系统建议扣分',
    evidence        TEXT          NULL COMMENT '证据（JSON 字符串数组）',
    note            VARCHAR(300)  NULL COMMENT '扣分说明',
    hazard_id       BIGINT        NULL COMMENT '转的整改隐患',
    hazard_no       VARCHAR(32)   NULL,
    sort            INT           NOT NULL DEFAULT 0,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_assessment (assessment_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准化自评考评项目';

-- 菜单：安全管理下面加【标准化自评】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(319,  3,   '标准化自评', 'C', 'T', '/safety/std', 'safety/std/index', 'std:list', 'std', 0, NOW(), NOW()),
(3191, 319, '开展自评、转整改', 'F', 'T', NULL, NULL, 'std:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',319,3191')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('319', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (319, 3191)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ===== 演示数据：大约 11 个月前完成的一次自评（80 分，二级），2 个扣分项转了整改并已闭环 =====
-- 今年还没自评：可以在页面上“新建自评”，系统自动取证，和这次对比
INSERT INTO std_assessment (id, tenant_id, title, year, status, leader_id, leader_name, members, start_date, finish_date, score, grade,
                            perf_dead, perf_serious, summary, create_time, update_time) VALUES
(9031, 1, CONCAT(YEAR(DATE_SUB(CURDATE(), INTERVAL 340 DAY)), ' 年度安全生产标准化自评'), YEAR(DATE_SUB(CURDATE(), INTERVAL 340 DAY)), 'DONE', 102, '张三（安全员）',
 '张三（组长）、李四、赵六、设备部王工', DATE_SUB(CURDATE(), INTERVAL 340 DAY), DATE_SUB(CURDATE(), INTERVAL 333 DAY), 80, '二级', 0, 0,
 '首次开展标准化自评，体系基本建立。主要问题：设备检验、隐患整改和临时变更管理不到位，制度和岗位操作规程需要补齐。扣分项已制定整改计划。',
 DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY));

INSERT INTO std_item (id, tenant_id, assessment_id, code, element, element_name, title, content, standard_score, method, auto, na, deduct, suggested,
                      evidence, note, hazard_id, hazard_no, sort, create_time, update_time) VALUES
(9201, 1, 9031, '1.1', 1, '目标职责', '安全生产目标', '制定年度安全生产目标和指标，分解到各部门，定期监测和考核完成情况', 4, '无年度目标不得分；未定期考核扣 1 分；综合得分低于 75 分扣 1 分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 1, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9202, 1, 9031, '1.2', 1, '目标职责', '机构和职责', '设置安全生产管理机构，配备专职安全生产管理人员；主要负责人履行第一责任人职责', 2, '未设置机构或未配备专职人员不得分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 2, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9203, 1, 9031, '1.3', 1, '目标职责', '全员参与', '建立全员安全生产责任制，逐级签订安全生产责任书，并对落实情况进行考核', 4, '无责任书不得分；每缺 1 人签署扣 0.5 分，最多扣 3 分', 1, 0, 1, 1, NULL, '2 名新入职员工未签订安全生产责任书', NULL, NULL, 3, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9204, 1, 9031, '1.4', 1, '目标职责', '安全生产投入', '按规定足额提取安全生产费用，编制使用计划，建立使用台账，专款专用', 4, '无年度计划扣 2 分；已用超过可用额度扣 1 分；每有 1 类超预算扣 0.5 分，最多 1 分', 1, 0, 1, 1, NULL, '安全费用台账未按使用类别归集，部分支出类别不清', NULL, NULL, 4, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9205, 1, 9031, '1.5', 1, '目标职责', '安全文化建设', '开展安全文化建设，鼓励从业人员报告事故隐患并给予奖励', 2, '未开展不得分；无隐患报告奖励扣 1 分', 0, 0, 1, NULL, NULL, '未建立从业人员报告事故隐患的奖励制度', 9021, CONCAT('YH', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 333 DAY), '%Y%m%d'), '-9021'), 5, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9206, 1, 9031, '2.1', 2, '制度化管理', '法规标准识别', '建立安全生产法律法规、标准规范清单，及时识别、获取和更新', 3, '无清单不得分；未及时更新扣 1 分', 0, 0, 1, NULL, NULL, '法律法规清单未纳入 2025 年新修订的标准', NULL, NULL, 6, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9207, 1, 9031, '2.2', 2, '制度化管理', '规章制度', '建立健全安全生产规章制度，以文件形式发布并发放到相关岗位', 4, '缺少必要制度每项扣 1 分；未正式发布扣 2 分', 0, 0, 0, NULL, NULL, NULL, NULL, NULL, 7, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9208, 1, 9031, '2.3', 2, '制度化管理', '操作规程', '编制岗位安全操作规程，发放到岗位并组织培训', 3, '缺少岗位操作规程每个岗位扣 1 分', 0, 0, 1, NULL, NULL, '罐区装卸岗位、污水站岗位缺少岗位安全操作规程', 9022, CONCAT('YH', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 333 DAY), '%Y%m%d'), '-9022'), 8, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9209, 1, 9031, '3.1', 3, '教育培训', '教育培训管理', '制定年度培训计划，组织安全培训和考试，建立培训档案', 5, '全年没有考试不得分；考试少于 2 次扣 1 分；合格率低于 90% 扣 1 分', 1, 0, 1, 1, NULL, '全年只组织了 1 次全员考试', NULL, NULL, 9, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9210, 1, 9031, '3.2', 3, '教育培训', '人员资质', '特种作业人员、安全管理人员持证上岗，证书在有效期内', 5, '每有 1 本证书过期扣 1 分', 1, 0, 1, 1, NULL, '1 名电工特种作业操作证过期未复审', NULL, NULL, 10, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9211, 1, 9031, '4.1', 4, '现场管理', '设备设施管理', '建立设备设施台账，特种设备、安全附件、检测报警仪器按期检验', 6, '每有 1 台在用设备逾期未检或从未检验扣 1 分', 1, 0, 2, 2, NULL, 'R-201 压力表、叉车逾期未检验', NULL, NULL, 11, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9212, 1, 9031, '4.2', 4, '现场管理', '作业安全', '动火、受限空间等特殊作业实行作业票审批，现场安全措施落实到位', 6, '全年没有作业票记录不得分；超过计划时间仍未结束的作业每张扣 1 分', 1, 0, 1, 1, NULL, '1 张动火作业票超过计划时间未及时关闭', NULL, NULL, 12, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9213, 1, 9031, '4.3', 4, '现场管理', '危险化学品管理', '建立危险化学品台账，安全技术说明书（MSDS）齐全，储存量不超过设计最大储量', 4, '每缺 1 份 MSDS 扣 0.5 分，最多 2 分；有超储扣 2 分', 1, 0, 1, 1, NULL, '缺乙酸乙酯、甲苯 2 份 MSDS', NULL, NULL, 13, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9214, 1, 9031, '4.4', 4, '现场管理', '相关方管理', '对承包商进行资质审查、入厂安全培训和作业过程监督', 3, '资质或保险快到期 / 过期、人员培训过期，每类扣 1 分', 1, 0, 1, 1, NULL, '1 家承包商安全生产许可证过期', NULL, NULL, 14, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9215, 1, 9031, '4.5', 4, '现场管理', '职业健康', '接触职业病危害的人员按期体检，职业禁忌证人员调离接害岗位', 3, '有职业禁忌证人员仍在接害岗位扣 2 分；体检逾期或未体检扣 1 分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 15, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9216, 1, 9031, '4.6', 4, '现场管理', '劳动防护用品', '按标准配备劳动防护用品，到期及时更换', 2, '有过期未更换扣 1 分；库存不足扣 1 分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 16, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9217, 1, 9031, '5.1', 5, '安全风险管控及隐患排查治理', '安全风险管理', '开展安全风险辨识评估，确定风险等级，落实分级管控和定期巡检', 6, '未辨识风险点不得分；每个风险点巡检逾期扣 1 分，最多 4 分', 1, 0, 1, 1, NULL, '罐区风险点巡检逾期', NULL, NULL, 17, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9218, 1, 9031, '5.2', 5, '安全风险管控及隐患排查治理', '重大危险源辨识与管理', '按 GB 18218 辨识重大危险源，确定等级并落实监控措施', 3, '未开展辨识不得分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 18, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9219, 1, 9031, '5.3', 5, '安全风险管控及隐患排查治理', '隐患排查治理', '定期开展隐患排查，隐患按期整改、验收闭环', 7, '逾期未整改的隐患每条扣 1 分，最多 4 分；按期整改率低于 90% 扣 1 分；有安全检查逾期未完成扣 1 分', 1, 0, 2, 2, NULL, '2 条隐患逾期未整改', NULL, NULL, 19, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9220, 1, 9031, '5.4', 5, '安全风险管控及隐患排查治理', '变更管理', '工艺、设备、人员变更实行风险评估和审批，临时变更按期恢复', 4, '每项临时变更超期未恢复扣 2 分', 1, 0, 2, 2, NULL, '1 项临时变更超期未恢复', NULL, NULL, 20, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9221, 1, 9031, '6.1', 6, '应急管理', '应急准备', '编制应急预案，按规定周期组织演练并评估', 5, '没有应急预案不得分；每个预案未按期演练扣 1 分，最多 3 分', 1, 0, 1, 1, NULL, '硫化氢中毒现场处置方案未按期演练', NULL, NULL, 21, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9222, 1, 9031, '6.2', 6, '应急管理', '应急设施和物资', '配备消防和应急救援设施、物资，定期检查维护', 2, '每台消防或应急设施逾期未检扣 1 分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 22, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9223, 1, 9031, '6.3', 6, '应急管理', '应急处置与评估', '发生事件后按预案及时响应处置，结束后进行总结评估', 3, '未按预案处置或未总结评估的每起扣 1 分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 23, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9224, 1, 9031, '7.1', 7, '事故管理', '事故报告', '发生事故按规定及时报告，不迟报、漏报、谎报、瞒报', 2, '有迟报、漏报不得分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 24, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9225, 1, 9031, '7.2', 7, '事故管理', '事故调查和处理', '按“四不放过”原则调查处理事故，落实整改措施', 4, '每起调查超期未结案扣 2 分；每项整改措施逾期扣 1 分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 25, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9226, 1, 9031, '8.1', 8, '持续改进', '绩效评定', '每年至少开展一次安全生产标准化自评，评价体系运行效果', 2, '一年内没有完成自评不得分', 1, 0, 2, 2, NULL, '首次开展标准化自评', NULL, NULL, 26, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9227, 1, 9031, '8.2', 8, '持续改进', '持续改进', '根据自评和安全目标完成情况制定改进措施并落实', 2, '上次自评扣分项转的整改隐患未闭环扣 1 分；安全目标有红灯指标扣 1 分', 1, 0, 0, 0, NULL, NULL, NULL, NULL, 27, DATE_SUB(NOW(), INTERVAL 340 DAY), DATE_SUB(NOW(), INTERVAL 333 DAY));

-- 扣分项转的整改隐患（已闭环）
INSERT INTO hazard (id, tenant_id, hazard_no, title, description, category, level, location, reporter_id, reporter_name,
                    rectifier_id, rectifier_name, deadline, rectify_measure, rectify_time, verifier_id, verifier_name, verify_time,
                    status, version, create_time, update_time) VALUES
(9021, 1, CONCAT('YH', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 333 DAY), '%Y%m%d'), '-9021'), '标准化自评扣分：1.5 安全文化建设', '未建立从业人员报告事故隐患的奖励制度', '安全管理', 'GENERAL', '全厂（标准化自评）', 102, '张三（安全员）',
    102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 303 DAY), '建立隐患报告奖励制度并发布，隐患随手拍每条奖励 20~200 元', DATE_SUB(NOW(), INTERVAL 310 DAY), 101, '演示企业管理员', DATE_SUB(NOW(), INTERVAL 308 DAY), 'CLOSED', 4, DATE_SUB(NOW(), INTERVAL 333 DAY), DATE_SUB(NOW(), INTERVAL 308 DAY)),
(9022, 1, CONCAT('YH', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 333 DAY), '%Y%m%d'), '-9022'), '标准化自评扣分：2.3 操作规程', '罐区装卸岗位、污水站岗位缺少岗位安全操作规程', '安全管理', 'GENERAL', '全厂（标准化自评）', 102, '张三（安全员）',
    102, '张三（安全员）', DATE_SUB(NOW(), INTERVAL 303 DAY), '编制罐区装卸、污水站岗位安全操作规程，已发放到岗位并组织培训考试', DATE_SUB(NOW(), INTERVAL 310 DAY), 101, '演示企业管理员', DATE_SUB(NOW(), INTERVAL 308 DAY), 'CLOSED', 4, DATE_SUB(NOW(), INTERVAL 333 DAY), DATE_SUB(NOW(), INTERVAL 308 DAY));

INSERT INTO hazard_log (id, tenant_id, hazard_id, action, from_status, to_status, operator_id, operator_name, comment, create_time) VALUES
(9301, 1, 9021, 'REPORT', NULL, 'REPORTED', 102, '张三（安全员）', NULL, DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9302, 1, 9021, 'ASSIGN', 'REPORTED', 'RECTIFYING', 101, '演示企业管理员', '30 天内完成', DATE_ADD(DATE_SUB(NOW(), INTERVAL 333 DAY), INTERVAL 1 HOUR)),
(9303, 1, 9021, 'RECTIFY', 'RECTIFYING', 'PENDING_VERIFY', 102, '张三（安全员）', '建立隐患报告奖励制度并发布，隐患随手拍每条奖励 20~200 元', DATE_SUB(NOW(), INTERVAL 310 DAY)),
(9304, 1, 9021, 'VERIFY_PASS', 'PENDING_VERIFY', 'CLOSED', 101, '演示企业管理员', '已核查文件和培训记录', DATE_SUB(NOW(), INTERVAL 308 DAY)),
(9305, 1, 9022, 'REPORT', NULL, 'REPORTED', 102, '张三（安全员）', NULL, DATE_SUB(NOW(), INTERVAL 333 DAY)),
(9306, 1, 9022, 'ASSIGN', 'REPORTED', 'RECTIFYING', 101, '演示企业管理员', '30 天内完成', DATE_ADD(DATE_SUB(NOW(), INTERVAL 333 DAY), INTERVAL 1 HOUR)),
(9307, 1, 9022, 'RECTIFY', 'RECTIFYING', 'PENDING_VERIFY', 102, '张三（安全员）', '编制罐区装卸、污水站岗位安全操作规程，已发放到岗位并组织培训考试', DATE_SUB(NOW(), INTERVAL 310 DAY)),
(9308, 1, 9022, 'VERIFY_PASS', 'PENDING_VERIFY', 'CLOSED', 101, '演示企业管理员', '已核查文件和培训记录', DATE_SUB(NOW(), INTERVAL 308 DAY));
