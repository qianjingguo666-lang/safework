-- 第三十六部分：应急演练升级（参演人员和签到、按 AQ/T 9009 评估打分、预案适宜性、问题转隐患整改、年度演练计划）

ALTER TABLE emergency_drill
    ADD COLUMN scenario       VARCHAR(500)  NULL COMMENT '演练情景' AFTER location,
    ADD COLUMN organizer_name VARCHAR(50)   NULL COMMENT '安排人' AFTER scenario,
    ADD COLUMN total_score    DECIMAL(4,1)  NULL COMMENT '评估总分（百分制，按适用项目折算）' AFTER recorder_name,
    ADD COLUMN grade          VARCHAR(10)   NULL COMMENT '优秀 / 良好 / 合格 / 不合格' AFTER total_score,
    ADD COLUMN plan_suitable  TINYINT       NULL COMMENT '预案是否适宜' AFTER grade,
    ADD COLUMN revise_note    VARCHAR(300)  NULL COMMENT '预案修订意见' AFTER plan_suitable,
    ADD COLUMN evaluator_name VARCHAR(50)   NULL AFTER revise_note,
    ADD COLUMN evaluate_time  DATETIME      NULL AFTER evaluator_name,
    MODIFY COLUMN status      VARCHAR(10)   NOT NULL COMMENT 'PLANNED / DONE 待评估 / EVALUATED 已评估 / CANCELLED';

ALTER TABLE emergency_plan
    ADD COLUMN revise_note VARCHAR(300) NULL COMMENT '演练评估提出的修订意见，修改预案后清空' AFTER updater_name,
    ADD COLUMN revise_time DATETIME     NULL AFTER revise_note;

CREATE TABLE emergency_drill_participant (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '企业',
    drill_id    BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    user_name   VARCHAR(50)  NOT NULL,
    dept_name   VARCHAR(50)  NULL,
    role        VARCHAR(10)  NOT NULL COMMENT 'COMMAND / RESCUE / EVACUATE / MEDICAL / SUPPORT / OBSERVE',
    sign_time   DATETIME     NULL COMMENT '本人签到时间',
    attended    TINYINT      NULL COMMENT '登记结果时确认：1 参加 0 缺席',
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_drill (tenant_id, drill_id),
    KEY idx_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演练参演人员';

CREATE TABLE emergency_drill_score (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '企业',
    drill_id    BIGINT       NOT NULL,
    item_code   VARCHAR(10)  NOT NULL COMMENT '评估项目，见 DrillRules.SCORE_ITEMS',
    score       DECIMAL(4,1) NULL COMMENT 'NULL 表示不适用',
    remark      VARCHAR(200) NULL,
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_drill (tenant_id, drill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演练评估打分';

CREATE TABLE emergency_drill_problem (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '企业',
    drill_id    BIGINT       NOT NULL,
    category    VARCHAR(10)  NOT NULL COMMENT 'PLAN / EQUIPMENT / TRAINING / ORGANIZE / OTHER',
    description VARCHAR(300) NOT NULL,
    hazard_id   BIGINT       NULL COMMENT '转成的隐患',
    hazard_no   VARCHAR(30)  NULL,
    create_time DATETIME     NULL,
    update_time DATETIME     NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_drill (tenant_id, drill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演练发现的问题';

-- ===== 演练数据 =====
-- 9301 受限空间实战演练：已评估 82 分（良好），2 个问题还没转整改
-- 9302 甲醇储罐桌面推演：已评估（医疗救护、后期处置不适用），认为预案要修订（疏散路线）
-- 9303 高处坠落救援演练（12 天后）：补上演练情景和参演人员
-- 9304 甲醇储罐泄漏实战演练（5 天前）：已登记结果，还没评估
UPDATE emergency_drill SET status = 'EVALUATED', organizer_name = '张三（安全员）',
       scenario = '清淤作业人员在调节池内晕倒，监护人发现后呼救，救援组佩戴空气呼吸器进入施救',
       total_score = 82.0, grade = '良好', plan_suitable = 1, evaluator_name = '张三（安全员）', evaluate_time = '2026-04-16 10:00:00'
WHERE id = 9301 AND status = 'DONE';
UPDATE emergency_drill SET status = 'EVALUATED', organizer_name = '张三（安全员）',
       scenario = 'T-102 甲醇储罐底部法兰大量泄漏，可燃气体报警，西北风 3 级',
       total_score = 83.8, grade = '良好', plan_suitable = 0, revise_note = '预案没写下风向疏散路线和集合点，要补充疏散路线图和集合点位置',
       evaluator_name = '张三（安全员）', evaluate_time = '2026-03-21 09:00:00'
WHERE id = 9302 AND status = 'DONE';
UPDATE emergency_plan SET revise_note = '预案没写下风向疏散路线和集合点，要补充疏散路线图和集合点位置', revise_time = '2026-03-21 09:00:00'
WHERE id = 9103 AND revise_note IS NULL;
UPDATE emergency_drill SET organizer_name = '张三（安全员）',
       scenario = '检修人员在 1 号厂房屋顶作业时安全带挂点脱落，坠落到下层平台，腿部受伤无法行动'
WHERE id = 9303 AND scenario IS NULL;

INSERT INTO emergency_drill (id, tenant_id, plan_id, plan_title, title, drill_type, plan_time, location, scenario, organizer_name, status, actual_time,
                             participant_count, summary, problems, recorder_name, create_time, update_time)
SELECT 9304, 1, p.id, p.title, '甲醇储罐泄漏实战演练', 'LIVE', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '14:00:00'), '罐区 T-102 储罐',
       'T-102 储罐出料管线法兰泄漏，现场人员关阀堵漏，消防水幕稀释，下风向疏散', '张三（安全员）', 'DONE',
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '14:05:00'), 16,
       '从报警到关闭出料阀用时 4 分钟，水幕 6 分钟内投用，下风向人员按新标识疏散到 2 号集合点，清点人数准确。',
       '一名操作工戴防毒面具前没检查滤毒罐是否过期；对讲机信道切换混乱。', '张三（安全员）',
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 10 DAY), '09:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '16:00:00')
FROM emergency_plan p WHERE p.id = 9103 AND NOT EXISTS (SELECT 1 FROM emergency_drill d WHERE d.id = 9304);
UPDATE emergency_plan SET last_drill_time = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '14:05:00')
WHERE id = 9103 AND EXISTS (SELECT 1 FROM emergency_drill d WHERE d.id = 9304);

INSERT INTO emergency_drill_participant (id, tenant_id, drill_id, user_id, user_name, dept_name, role, sign_time, attended, create_time, update_time)
SELECT v.id, 1, v.drill_id, v.user_id, u.nickname, d.dept_name, v.role, v.sign_time, v.attended, NOW(), NOW()
FROM (
    SELECT 93011 AS id, 9301 AS drill_id, 102 AS user_id, 'COMMAND' AS role, CAST('2026-04-15 08:50:00' AS DATETIME) AS sign_time, 1 AS attended
    UNION ALL SELECT 93012, 9301, 103, 'RESCUE', CAST('2026-04-15 08:52:00' AS DATETIME), 1
    UNION ALL SELECT 93013, 9301, 104, 'RESCUE', CAST('2026-04-15 08:55:00' AS DATETIME), 1
    UNION ALL SELECT 93021, 9302, 102, 'COMMAND', CAST('2026-03-20 13:50:00' AS DATETIME), 1
    UNION ALL SELECT 93022, 9302, 103, 'EVACUATE', CAST('2026-03-20 13:55:00' AS DATETIME), 1
    UNION ALL SELECT 93023, 9302, 104, 'OBSERVE', NULL, 0
    UNION ALL SELECT 93031, 9303, 102, 'COMMAND', NULL, NULL
    UNION ALL SELECT 93032, 9303, 103, 'RESCUE', NULL, NULL
    UNION ALL SELECT 93033, 9303, 104, 'EVACUATE', NULL, NULL
    UNION ALL SELECT 93041, 9304, 102, 'COMMAND', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '13:40:00'), 1
    UNION ALL SELECT 93042, 9304, 103, 'RESCUE', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '13:45:00'), 1
    UNION ALL SELECT 93043, 9304, 104, 'EVACUATE', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 5 DAY), '13:46:00'), 1
) v
JOIN emergency_drill dr ON dr.id = v.drill_id
JOIN sys_user u ON u.id = v.user_id
LEFT JOIN sys_dept d ON d.id = u.dept_id
WHERE NOT EXISTS (SELECT 1 FROM emergency_drill_participant x WHERE x.id = v.id);

INSERT INTO emergency_drill_score (id, tenant_id, drill_id, item_code, score, remark, create_time, update_time)
SELECT v.id, 1, v.drill_id, v.item_code, v.score, v.remark, NOW(), NOW()
FROM (
    SELECT 93101 AS id, 9301 AS drill_id, 'REPORT' AS item_code, 9.0 AS score, '监护人 1 分钟内呼救并报告调度' AS remark
    UNION ALL SELECT 93102, 9301, 'COMMAND', 13.0, '现场指挥明确'
    UNION ALL SELECT 93103, 9301, 'COMM', 8.0, NULL
    UNION ALL SELECT 93104, 9301, 'DISPOSAL', 16.0, '6 分钟救出，入池前没复测气体，扣分'
    UNION ALL SELECT 93105, 9301, 'EVACUATE', 13.0, NULL
    UNION ALL SELECT 93106, 9301, 'EQUIP', 6.0, '三脚架展开慢，气瓶压力检查不熟练'
    UNION ALL SELECT 93107, 9301, 'MEDICAL', 9.0, NULL
    UNION ALL SELECT 93108, 9301, 'RECOVERY', 8.0, NULL
    UNION ALL SELECT 93201, 9302, 'REPORT', 9.0, NULL
    UNION ALL SELECT 93202, 9302, 'COMMAND', 14.0, '各岗位职责清楚'
    UNION ALL SELECT 93203, 9302, 'COMM', 9.0, NULL
    UNION ALL SELECT 93204, 9302, 'DISPOSAL', 17.0, NULL
    UNION ALL SELECT 93205, 9302, 'EVACUATE', 10.0, '下风向疏散路线不清楚'
    UNION ALL SELECT 93206, 9302, 'EQUIP', 8.0, NULL
    UNION ALL SELECT 93207, 9302, 'MEDICAL', NULL, '桌面推演不涉及'
    UNION ALL SELECT 93208, 9302, 'RECOVERY', NULL, '桌面推演不涉及'
) v
WHERE EXISTS (SELECT 1 FROM emergency_drill d WHERE d.id = v.drill_id AND d.status = 'EVALUATED')
  AND NOT EXISTS (SELECT 1 FROM emergency_drill_score x WHERE x.id = v.id);

INSERT INTO emergency_drill_problem (id, tenant_id, drill_id, category, description, create_time, update_time)
SELECT v.id, 1, v.drill_id, v.category, v.description, NOW(), NOW()
FROM (
    SELECT 93151 AS id, 9301 AS drill_id, 'EQUIPMENT' AS category, '救援三脚架展开速度慢，存放位置离调节池太远' AS description
    UNION ALL SELECT 93152, 9301, 'TRAINING', '部分救援人员空气呼吸器气瓶压力检查不熟练'
    UNION ALL SELECT 93251, 9302, 'PLAN', '预案没有下风向疏散路线和集合点'
) v
WHERE EXISTS (SELECT 1 FROM emergency_drill d WHERE d.id = v.drill_id AND d.status = 'EVALUATED')
  AND NOT EXISTS (SELECT 1 FROM emergency_drill_problem x WHERE x.id = v.id);
