-- 第十二部分：安全培训考试（题库、考试、答卷）

CREATE TABLE train_question (
    id           BIGINT        NOT NULL COMMENT '主键',
    tenant_id    BIGINT        NOT NULL COMMENT '企业',
    type         VARCHAR(10)   NOT NULL COMMENT 'SINGLE 单选 MULTI 多选 JUDGE 判断',
    category     VARCHAR(20)   NOT NULL COMMENT '分类',
    content      VARCHAR(500)  NOT NULL COMMENT '题干',
    option_list  TEXT          NOT NULL COMMENT '选项（JSON 数组）',
    answer       VARCHAR(10)   NOT NULL COMMENT '正确答案字母，多选如 ACD',
    analysis     VARCHAR(500)  NULL COMMENT '解析',
    source       VARCHAR(10)   NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 人工 / AI 生成',
    source_doc   VARCHAR(200)  NULL COMMENT 'AI 出题依据的文件',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '0 待审核 1 启用 2 停用',
    create_time  DATETIME      NULL,
    update_time  DATETIME      NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status, category, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训题库';

CREATE TABLE train_exam (
    id               BIGINT        NOT NULL COMMENT '主键',
    tenant_id        BIGINT        NOT NULL COMMENT '企业',
    title            VARCHAR(100)  NOT NULL COMMENT '考试名称',
    description      VARCHAR(500)  NULL,
    category         VARCHAR(20)   NULL COMMENT '抽题分类，空表示全部',
    single_count     INT           NOT NULL DEFAULT 0,
    single_score     INT           NOT NULL DEFAULT 2,
    multi_count      INT           NOT NULL DEFAULT 0,
    multi_score      INT           NOT NULL DEFAULT 4,
    judge_count      INT           NOT NULL DEFAULT 0,
    judge_score      INT           NOT NULL DEFAULT 2,
    total_score      INT           NOT NULL COMMENT '总分',
    pass_score       INT           NOT NULL COMMENT '及格分',
    duration_minutes INT           NOT NULL COMMENT '答题时长（分钟）',
    max_attempts     INT           NOT NULL DEFAULT 1 COMMENT '每人最多考几次',
    start_time       DATETIME      NOT NULL,
    end_time         DATETIME      NOT NULL,
    dept_ids         VARCHAR(500)  NULL COMMENT '选择的参考部门，空表示全体员工',
    dept_names       VARCHAR(500)  NULL,
    target_dept_ids  TEXT          NULL COMMENT '参考部门及下级部门（展开后）',
    show_answer      TINYINT       NOT NULL DEFAULT 1 COMMENT '交卷后是否显示答案和解析',
    status           VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 PUBLISHED 进行中 CLOSED 已结束',
    publish_time     DATETIME      NULL,
    creator_name     VARCHAR(50)   NULL,
    create_time      DATETIME      NULL,
    update_time      DATETIME      NULL,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试';

CREATE TABLE train_exam_record (
    id            BIGINT        NOT NULL COMMENT '主键',
    tenant_id     BIGINT        NOT NULL COMMENT '企业',
    exam_id       BIGINT        NOT NULL,
    exam_title    VARCHAR(100)  NOT NULL,
    user_id       BIGINT        NOT NULL,
    user_name     VARCHAR(50)   NULL,
    dept_id       BIGINT        NULL,
    dept_name     VARCHAR(50)   NULL,
    attempt_no    INT           NOT NULL COMMENT '第几次考',
    paper         MEDIUMTEXT    NOT NULL COMMENT '试卷快照（JSON，含答案）',
    answers       TEXT          NULL COMMENT '考生答案（JSON）',
    score         INT           NOT NULL DEFAULT 0,
    total_score   INT           NOT NULL,
    passed        TINYINT       NOT NULL DEFAULT 0,
    status        VARCHAR(12)   NOT NULL COMMENT 'IN_PROGRESS 答题中 SUBMITTED 已交卷',
    start_time    DATETIME      NOT NULL,
    deadline      DATETIME      NOT NULL COMMENT '交卷截止时间',
    submit_time   DATETIME      NULL,
    auto_submit   TINYINT       NOT NULL DEFAULT 0 COMMENT '1 超时自动交卷',
    create_time   DATETIME      NULL,
    update_time   DATETIME      NULL,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 同一个人同一场考试的第 N 次只能有一张卷：防止连点“开始考试”生成两张
    UNIQUE KEY uk_attempt (exam_id, user_id, attempt_no),
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_status_deadline (status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答卷';

-- 菜单：顶级【安全培训】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(7,    0,   '安全培训', 'M', 'T', '/training', NULL, NULL, 'training', 25, NOW(), NOW()),
(700,  7,   '我的考试', 'C', 'T', '/training/my', 'training/my/index', 'training:my', 'exam', 1, NOW(), NOW()),
(701,  7,   '题库',     'C', 'T', '/training/question', 'training/question/index', 'training:question:list', 'question', 2, NOW(), NOW()),
(7011, 701, '编辑题目', 'F', 'T', NULL, NULL, 'training:question:edit', NULL, 1, NOW(), NOW()),
(7012, 701, 'AI 出题',  'F', 'T', NULL, NULL, 'training:question:ai', NULL, 2, NOW(), NOW()),
(702,  7,   '考试管理', 'C', 'T', '/training/exam', 'training/exam/index', 'training:exam:list', 'exam-manage', 3, NOW(), NOW()),
(7021, 702, '编辑考试', 'F', 'T', NULL, NULL, 'training:exam:edit', NULL, 1, NOW(), NOW());

-- 套餐：旗舰版、标准版有培训考试；AI 出题只有含 AI 的旗舰版
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',7,700,701,7011,702,7021')
WHERE id IN (1, 2) AND FIND_IN_SET('700', menu_ids) = 0;
UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',7012')
WHERE FIND_IN_SET('400', menu_ids) > 0 AND FIND_IN_SET('7012', menu_ids) = 0;

-- 角色：安全员管理题库和考试；作业负责人、监护人参加考试
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (7, 700, 701, 7011, 7012, 702, 7021)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (7, 700)
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示化工的题库（内容来自知识库示例文件《特殊作业安全管理制度（示例）》）
INSERT INTO train_question (id, tenant_id, type, category, content, option_list, answer, analysis, source, status, create_time, update_time) VALUES
(1,  1, 'SINGLE', '受限空间作业', '进入受限空间作业，氧含量的合格范围是？', '["19.5%~23.5%","18%~21%","21%~25%","不低于 16% 即可"]', 'A', '制度 2.2：氧含量应在 19.5% 至 23.5% 之间。', 'MANUAL', 1, NOW(), NOW()),
(2,  1, 'SINGLE', '受限空间作业', '受限空间作业前的气体检测，最早可以在作业开始前多长时间进行？', '["30 分钟","2 小时","4 小时","前一天下班前"]', 'A', '制度 2.2：气体检测时间不得早于作业开始前 30 分钟。', 'MANUAL', 1, NOW(), NOW()),
(3,  1, 'SINGLE', '受限空间作业', '受限空间作业中断超过多长时间，恢复作业前必须重新检测？', '["10 分钟","30 分钟","1 小时","不需要重新检测"]', 'B', '制度 2.2：作业中断超过 30 分钟，恢复作业前必须重新检测。', 'MANUAL', 1, NOW(), NOW()),
(4,  1, 'SINGLE', '受限空间作业', '受限空间作业时，监护人应每隔多长时间确认一次作业人员状态？', '["5 分钟","10 分钟","30 分钟","1 小时"]', 'B', '制度 2.3：监护人每隔 10 分钟确认一次作业人员状态。', 'MANUAL', 1, NOW(), NOW()),
(5,  1, 'SINGLE', '受限空间作业', '受限空间内存在缺氧风险时，作业人员应佩戴哪种防护用品？', '["过滤式防毒面具","普通口罩","正压式空气呼吸器","防尘口罩"]', 'C', '制度 2.3：应佩戴正压式空气呼吸器或长管面具，严禁使用过滤式防毒面具代替。', 'MANUAL', 1, NOW(), NOW()),
(6,  1, 'MULTI',  '受限空间作业', '进入受限空间前，正确的做法有哪些？', '["物料管线加装盲板隔离","切断转动设备电源并挂“禁止合闸”牌","向受限空间内通入纯氧加快通风","先通风、再检测、后作业"]', 'ABD', '制度 2.1：隔离、断电挂牌，先通风再检测后作业；严禁通入纯氧。', 'MANUAL', 1, NOW(), NOW()),
(7,  1, 'MULTI',  '受限空间作业', '受限空间作业现场应配备的救援器材包括？', '["三脚架","安全绳","正压式空气呼吸器","过滤式防毒面具"]', 'ABC', '制度 2.4：应配备三脚架、安全绳、正压式空气呼吸器等救援器材。', 'MANUAL', 1, NOW(), NOW()),
(8,  1, 'JUDGE',  '受限空间作业', '发生中毒窒息事故时，监护人应立即进入受限空间把人拉出来。', '["正确","错误"]', 'B', '制度 2.4：严禁盲目施救，救援人员必须佩戴正压式空气呼吸器、系好安全绳后方可进入。', 'MANUAL', 1, NOW(), NOW()),
(9,  1, 'JUDGE',  '受限空间作业', '容积较大的受限空间，应对上、中、下各部位分别进行气体检测。', '["正确","错误"]', 'A', '制度 2.2：容积较大的空间应对上、中、下各部位分别检测。', 'MANUAL', 1, NOW(), NOW()),
(10, 1, 'SINGLE', '动火作业', '动火作业前，应清除动火点周围多大范围内的可燃物？', '["5 米","10 米","15 米","30 米"]', 'C', '制度第三章：应清除动火点周围 15 米范围内的可燃物。', 'MANUAL', 1, NOW(), NOW()),
(11, 1, 'SINGLE', '动火作业', '动火现场应配备不少于几具灭火器？', '["1 具","2 具","3 具","5 具"]', 'B', '制度第三章：动火现场应配备不少于 2 具灭火器。', 'MANUAL', 1, NOW(), NOW()),
(12, 1, 'MULTI',  '动火作业', '关于动火作业，下列说法正确的有？', '["动火点周围的下水井、地漏、地沟应进行封堵","必须设专人监火","动火结束后监火人确认无残留火种方可离开","监火人可以同时兼做其他工作"]', 'ABC', '制度第三章：封堵下水井地漏地沟，设专人监火，结束后确认无残留火种。', 'MANUAL', 1, NOW(), NOW()),
(13, 1, 'JUDGE',  '动火作业', '遇五级以上大风，应停止室外动火作业。', '["正确","错误"]', 'A', '制度第三章：遇五级以上大风，应停止室外动火作业。', 'MANUAL', 1, NOW(), NOW()),
(14, 1, 'SINGLE', '高处作业', '在坠落高度基准面多少米以上（含）有可能坠落的高处进行的作业为高处作业？', '["1.5 米","2 米","3 米","5 米"]', 'B', '制度第四章：坠落高度基准面 2 米以上（含 2 米）。', 'MANUAL', 1, NOW(), NOW()),
(15, 1, 'SINGLE', '高处作业', '高处作业安全带的正确使用方法是？', '["低挂高用","高挂低用","挂在腰部以下的任意位置","不需要系挂"]', 'B', '制度第四章：安全带应高挂低用，挂在牢固可靠的构件上。', 'MANUAL', 1, NOW(), NOW()),
(16, 1, 'MULTI',  '高处作业', '高处作业中，下列做法正确的有？', '["工具、材料放入工具袋","作业下方设置警戒区域","为节省时间把工具从上往下抛","作业平台边缘设置防护栏杆"]', 'ABD', '制度第四章：工具入袋、设警戒区、设防护栏杆，严禁上下抛掷。', 'MANUAL', 1, NOW(), NOW()),
(17, 1, 'JUDGE',  '高处作业', '遇暴雨、大雾等恶劣天气，可以继续进行室外高处作业，但要加强监护。', '["正确","错误"]', 'B', '制度第四章：遇恶劣天气应停止室外高处作业。', 'MANUAL', 1, NOW(), NOW()),
(18, 1, 'JUDGE',  '通用安全', '作业票有效期原则上不超过一个班次（8 小时），超过时应重新办理。', '["正确","错误"]', 'A', '制度第五章：作业票有效期原则上不超过一个班次（8 小时）。', 'MANUAL', 1, NOW(), NOW()),
(19, 1, 'SINGLE', '通用安全', '特殊作业期间，监护人的正确做法是？', '["可以暂时离开去拿工具","作业全过程现场监护，不得离开","只在作业开始时到场","由作业人员自己兼任"]', 'B', '制度第一章：监护人负责作业全过程的现场监护，作业期间不得离开现场。', 'MANUAL', 1, NOW(), NOW()),
(20, 1, 'JUDGE',  '通用安全', '作业地点发生变化时，可以沿用原来的作业票。', '["正确","错误"]', 'B', '制度第五章：作业内容、地点或人员变化时，应重新办理作业票。', 'MANUAL', 1, NOW(), NOW());

-- 演示考试：全体员工，单选 5×8 + 多选 2×10 + 判断 5×8 = 100 分，60 分及格，20 分钟，可考 2 次
INSERT INTO train_exam (id, tenant_id, title, description, category, single_count, single_score, multi_count, multi_score,
                        judge_count, judge_score, total_score, pass_score, duration_minutes, max_attempts, start_time, end_time,
                        show_answer, status, publish_time, creator_name, create_time, update_time) VALUES
(1, 1, '特殊作业安全知识考试（第四季度）', '覆盖受限空间、动火、高处作业和作业票管理，每人随机抽题。', NULL,
    5, 8, 2, 10, 5, 8, 100, 60, 20, 2, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY),
    1, 'PUBLISHED', NOW(), '演示企业管理员', NOW(), NOW());
