-- 第三十八部分：作业安全分析（JSA）：作业步骤 → 危害 → 风险（可能性 × 严重度）→ 控制措施；编写、AI 起草、审核发布、开作业票时引用、满 1 年复审

CREATE TABLE jsa (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    jsa_no         VARCHAR(30)   NOT NULL,
    title          VARCHAR(60)   NOT NULL,
    ticket_type    VARCHAR(20)   NULL COMMENT '适用的作业类型，NULL 为通用',
    work_content   VARCHAR(500)  NOT NULL,
    location       VARCHAR(100)  NULL,
    ppe            VARCHAR(300)  NULL COMMENT '劳动防护用品',
    steps          TEXT          NOT NULL COMMENT '步骤 JSON：[{step, hazards, likelihood, severity, controls}]',
    step_count     INT           NOT NULL DEFAULT 0,
    max_risk       INT           NOT NULL DEFAULT 0 COMMENT '最高风险值 L×S',
    ai_drafted     TINYINT       NOT NULL DEFAULT 0,
    status         VARCHAR(10)   NOT NULL COMMENT 'DRAFT / REVIEWING / PUBLISHED / REJECTED / ARCHIVED',
    author_id      BIGINT        NOT NULL,
    author_name    VARCHAR(50)   NOT NULL,
    reviewer_id    BIGINT        NULL,
    reviewer_name  VARCHAR(50)   NULL,
    review_time    DATETIME      NULL,
    review_comment VARCHAR(200)  NULL,
    publish_time   DATETIME      NULL,
    copied_from    BIGINT        NULL COMMENT '复制来源（复审）',
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_tenant_type (tenant_id, ticket_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业安全分析';

ALTER TABLE work_ticket
    ADD COLUMN jsa_id    BIGINT       NULL COMMENT '引用的 JSA' AFTER approver_name,
    ADD COLUMN jsa_no    VARCHAR(30)  NULL AFTER jsa_id,
    ADD COLUMN jsa_title VARCHAR(60)  NULL AFTER jsa_no,
    ADD KEY idx_jsa (tenant_id, jsa_id);

-- 菜单：作业管理下面加【JSA 作业安全分析】；安全员能编写和审核，作业负责人能编写，监护人能看
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(202,  2,   'JSA 作业安全分析', 'C', 'T', '/ticket/jsa', 'ticket/jsa/index', 'jsa:list', 'jsa', 1, NOW(), NOW()),
(2021, 202, '编写 JSA', 'F', 'T', NULL, NULL, 'jsa:edit', NULL, 1, NOW(), NOW()),
(2022, 202, '审核 JSA', 'F', 'T', NULL, NULL, 'jsa:review', NULL, 2, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',202,2021,2022')
WHERE FIND_IN_SET('200', menu_ids) > 0 AND FIND_IN_SET('202', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m
  ON (r.role_code = 'safety' AND m.id IN (202, 2021, 2022))
  OR (r.role_code = 'leader' AND m.id IN (202, 2021))
  OR (r.role_code = 'guardian' AND m.id = 202)
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ===== 演示数据 =====
-- 9601 调节池清淤（已发布，2 个月前）；9602 罐区动火（已发布 400 天，该复审）；9603 屋顶高处作业（AI 起草，待张三审核）；9604 泵密封更换（李四的草稿）
INSERT INTO jsa (id, tenant_id, jsa_no, title, ticket_type, work_content, location, ppe, steps, step_count, max_risk, ai_drafted, status,
                 author_id, author_name, reviewer_id, reviewer_name, review_time, publish_time, create_time, update_time)
SELECT v.id, 1, v.jsa_no, v.title, v.ticket_type, v.work_content, v.location, v.ppe, v.steps, v.step_count, v.max_risk, v.ai_drafted, v.status,
       v.author_id, v.author_name, v.reviewer_id, v.reviewer_name, v.review_time, v.publish_time, v.create_time, COALESCE(v.publish_time, v.create_time)
FROM (
    SELECT 9601 AS id, 'JSA20260901-9601' AS jsa_no, '污水调节池清淤作业 JSA' AS title, 'CONFINED_SPACE' AS ticket_type, '进入 1# 污水调节池清理池底污泥（人工清淤，污泥吊运出池）' AS work_content, '污水处理站 1# 调节池' AS location, '安全帽、防化服、正压式空气呼吸器、安全带、救生绳、防滑胶靴、便携式四合一气体检测仪' AS ppe, '[{"step": "办理受限空间作业票、安全交底", "hazards": "作业人员不清楚调节池内硫化氢风险", "likelihood": 2, "severity": 4, "controls": "作业前由作业负责人交底，讲清硫化氢中毒症状和撤离信号；监护人持证在岗"}, {"step": "停泵、关闭进水阀并加盲板", "hazards": "污水继续流入，人员淹溺；搅拌器误启动", "likelihood": 3, "severity": 5, "controls": "关闭进水阀并在法兰处加盲板，挂牌上锁；切断提升泵和搅拌器电源，现场试启动确认无法启动"}, {"step": "排空池内污水、强制通风", "hazards": "池内积聚硫化氢、缺氧", "likelihood": 4, "severity": 5, "controls": "用轴流风机强制通风不少于 30 分钟，作业期间不间断通风；风机放在上风向，风管伸到池底"}, {"step": "三项气体检测", "hazards": "检测不准或检测后浓度变化", "likelihood": 3, "severity": 5, "controls": "进入前 30 分钟内在池内上、中、下部检测氧含量、可燃气、硫化氢；硫化氢 ≤10 mg/m³ 才能进入；作业中每 2 小时复测，人员佩戴便携式四合一检测仪"}, {"step": "下池清淤", "hazards": "搅动底泥释放硫化氢导致中毒；滑倒", "likelihood": 4, "severity": 5, "controls": "佩戴正压式空气呼吸器下池；系安全带和救生绳，绳端由监护人控制；池边放置三脚架，救援人员和装备就位"}, {"step": "污泥吊运出池", "hazards": "吊桶坠落砸伤池内人员", "likelihood": 2, "severity": 4, "controls": "吊运时池内人员避开吊桶下方；吊桶装载不超过三分之二"}, {"step": "撤离、清点人员和工具、恢复", "hazards": "人员或工具遗留池内；盲板未拆导致投用异常", "likelihood": 2, "severity": 3, "controls": "监护人清点人员和工具后签字；按隔离清单逐项拆除盲板、解锁送电"}]' AS steps, 7 AS step_count, 20 AS max_risk, 0 AS ai_drafted, 'PUBLISHED' AS status, 103 AS author_id, '李四（作业负责人）' AS author_name, 102 AS reviewer_id, '张三（安全员）' AS reviewer_name, DATE_SUB(NOW(), INTERVAL 60 DAY) AS review_time, DATE_SUB(NOW(), INTERVAL 60 DAY) AS publish_time, DATE_SUB(NOW(), INTERVAL 63 DAY) AS create_time
    UNION ALL SELECT 9602 AS id, 'JSA20250905-9602' AS jsa_no, '甲醇储罐区管线动火焊接 JSA' AS title, 'HOT_WORK' AS ticket_type, '罐区 DN100 甲醇出料管线更换阀门，切割、焊接' AS work_content, '罐区防火堤内' AS location, '安全帽、焊接面罩、阻燃工作服、焊接手套、绝缘鞋' AS ppe, '[{"step": "办理动火作业票、确定动火等级", "hazards": "在甲醇储罐区按二级动火办理，等级偏低", "likelihood": 2, "severity": 5, "controls": "罐区防火堤内动火按一级动火办理；作业前安全交底"}, {"step": "管线倒空、吹扫、加盲板", "hazards": "管线残留甲醇遇火爆炸", "likelihood": 3, "severity": 5, "controls": "倒空后用氮气吹扫置换；在动火管段两端加盲板隔离，挂牌上锁"}, {"step": "清理周围可燃物、封堵", "hazards": "周围地沟、阴井可燃气体被点燃", "likelihood": 3, "severity": 5, "controls": "清除动火点 15 米内可燃物；封堵地沟、阴井、排水口；用防火毯遮盖附近设备"}, {"step": "动火分析", "hazards": "可燃气体浓度超标", "likelihood": 3, "severity": 5, "controls": "动火前 30 分钟内取样分析可燃气体合格；作业中断超过 30 分钟重新分析"}, {"step": "焊接作业", "hazards": "火花飞溅引燃；焊工灼烫、触电", "likelihood": 3, "severity": 4, "controls": "配 2 具 8 kg 干粉灭火器并专人监火；电焊机外壳接地，焊把线无破损"}, {"step": "作业结束清理现场", "hazards": "残留火种", "likelihood": 2, "severity": 4, "controls": "检查确认无残留火种，监火人 30 分钟后复查签字"}]' AS steps, 6 AS step_count, 15 AS max_risk, 0 AS ai_drafted, 'PUBLISHED' AS status, 102 AS author_id, '张三（安全员）' AS author_name, 101 AS reviewer_id, '演示企业管理员' AS reviewer_name, DATE_SUB(NOW(), INTERVAL 400 DAY) AS review_time, DATE_SUB(NOW(), INTERVAL 400 DAY) AS publish_time, DATE_SUB(NOW(), INTERVAL 403 DAY) AS create_time
    UNION ALL SELECT 9603 AS id, 'JSA20261104-9603' AS jsa_no, '厂房屋顶检修高处作业 JSA' AS title, 'HEIGHT' AS ticket_type, '1 号厂房屋顶彩钢瓦更换和屋面排风机检修' AS work_content, '1 号厂房屋顶' AS location, '安全帽、双钩安全带、防滑鞋、防护手套' AS ppe, '[{"step": "办理高处作业票、确认作业人员状况", "hazards": "作业人员患有高血压等禁忌症", "likelihood": 2, "severity": 4, "controls": "作业前询问并确认身体状况；作业人员持高处作业证"}, {"step": "检查屋顶彩钢瓦承重和护栏", "hazards": "踩穿彩钢瓦坠落", "likelihood": 3, "severity": 5, "controls": "在檩条上铺设走道板，严禁直接踩踏彩钢瓦；临边设置临时护栏"}, {"step": "系挂安全带攀爬屋顶", "hazards": "攀爬时坠落", "likelihood": 3, "severity": 5, "controls": "使用双钩安全带，攀爬过程中始终有一个挂钩挂在牢固处；爬梯底部有人扶梯"}, {"step": "屋顶检修作业", "hazards": "人员滑倒坠落；工具坠落砸伤下方人员", "likelihood": 3, "severity": 5, "controls": "安全带系挂在生命绳上；工具系防坠绳；下方拉警戒线并派人看守"}, {"step": "撤离、清理屋顶", "hazards": "遗留工具材料", "likelihood": 2, "severity": 3, "controls": "清点工具材料后撤离；拆除警戒前确认屋顶无遗留物"}]' AS steps, 5 AS step_count, 15 AS max_risk, 1 AS ai_drafted, 'REVIEWING' AS status, 103 AS author_id, '李四（作业负责人）' AS author_name, NULL AS reviewer_id, NULL AS reviewer_name, NULL AS review_time, NULL AS publish_time, DATE_SUB(NOW(), INTERVAL 2 DAY) AS create_time
    UNION ALL SELECT 9604 AS id, 'JSA20261106-9604' AS jsa_no, '离心泵机械密封更换 JSA' AS title, NULL AS ticket_type, '储运车间 P-202 离心泵机械密封更换' AS work_content, '储运车间 2# 泵房' AS location, '安全帽、工作服、防割手套、护目镜、安全鞋' AS ppe, '[{"step": "关闭进出口阀门、停泵断电", "hazards": "介质泄漏、泵误启动", "likelihood": 3, "severity": 4, "controls": "关闭进出口阀门并挂牌；切断电源挂“禁止合闸”牌并上锁"}, {"step": "泄压排液", "hazards": "残余压力喷出介质伤人", "likelihood": 3, "severity": 4, "controls": "打开排净阀泄压排液至地漏，确认压力表归零"}, {"step": "拆卸更换机械密封", "hazards": "工具打滑割伤手；部件砸脚", "likelihood": 3, "severity": 3, "controls": "使用合适扳手，佩戴防割手套；重部件两人配合"}, {"step": "回装、试运行", "hazards": "密封泄漏；转动部件卷入", "likelihood": 2, "severity": 3, "controls": "盘车确认无卡涩后再送电；试运行时人员远离转动部件"}]' AS steps, 4 AS step_count, 12 AS max_risk, 0 AS ai_drafted, 'DRAFT' AS status, 103 AS author_id, '李四（作业负责人）' AS author_name, NULL AS reviewer_id, NULL AS reviewer_name, NULL AS review_time, NULL AS publish_time, DATE_SUB(NOW(), INTERVAL 2 DAY) AS create_time
) v
WHERE NOT EXISTS (SELECT 1 FROM jsa j WHERE j.id = v.id);
