-- 第二十四部分：安全生产责任制（岗位安全职责 → 年度责任书 → 本人手写签署 → 年度考核）

CREATE TABLE resp_duty (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    post_id        BIGINT        NOT NULL COMMENT '岗位（ppe_post）',
    duties         TEXT          NOT NULL COMMENT '职责清单（JSON 数组）',
    updater_name   VARCHAR(50)   NULL,
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_post (tenant_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位安全职责';

CREATE TABLE resp_book (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    year            INT           NOT NULL COMMENT '责任年度',
    title           VARCHAR(60)   NOT NULL,
    content         TEXT          NOT NULL COMMENT '通用条款',
    deadline        DATE          NOT NULL COMMENT '签署截止日期',
    status          VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT 草稿 / PUBLISHED 签署中 / CLOSED 已结束',
    publish_time    DATETIME      NULL,
    publisher_name  VARCHAR(50)   NULL,
    remark          VARCHAR(200)  NULL,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年度安全生产责任书';

CREATE TABLE resp_sign (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL COMMENT '企业',
    book_id         BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    user_name       VARCHAR(50)   NOT NULL,
    posts           VARCHAR(200)  NULL COMMENT '发布时所在岗位（快照）',
    duties          TEXT          NOT NULL COMMENT '发布时的岗位职责（JSON 数组快照）',
    status          VARCHAR(8)    NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待签署 / SIGNED 已签署',
    sign_time       DATETIME      NULL,
    sign_image      MEDIUMTEXT    NULL COMMENT '手写签名 PNG data URL',
    sign_hash       CHAR(64)      NULL COMMENT '签名图片 SHA-256',
    score           INT           NULL COMMENT '年度考核得分 0~100',
    grade           VARCHAR(8)    NULL COMMENT '优秀 / 良好 / 合格 / 不合格',
    assess_comment  VARCHAR(300)  NULL,
    assessor_name   VARCHAR(50)   NULL,
    assess_time     DATETIME      NULL,
    version         INT           NOT NULL DEFAULT 0,
    create_time     DATETIME      NULL,
    update_time     DATETIME      NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_book_user (book_id, user_id, deleted),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='责任书签署与考核';

-- 菜单：安全管理下面加【安全生产责任制】
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(312,  3,   '安全生产责任制', 'C', 'T', '/safety/resp', 'safety/resp/index', 'resp:list', 'resp', 13, NOW(), NOW()),
(3121, 312, '管理职责、发布责任书、考核', 'F', 'T', NULL, NULL, 'resp:edit', NULL, 1, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',312,3121')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('312', menu_ids) = 0;

-- 安全员管理；作业负责人、监护人看、签自己的
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id IN (312, 3121)
WHERE r.role_code = 'safety' AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m ON m.id = 312
WHERE r.role_code IN ('leader', 'guardian') AND r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 演示岗位职责（岗位来自劳保模块 V17）
INSERT INTO resp_duty (id, tenant_id, post_id, duties, updater_name, create_time, update_time) VALUES
(9901, 1, 9711, '["严格执行罐区操作规程和交接班制度，认真填写运行记录", "按规定巡检储罐液位、温度、压力和呼吸阀，发现异常立即报告", "进入受限空间、动火等危险作业前必须办理作业票，未经批准不作业", "正确佩戴和使用劳动防护用品，爱护消防器材和应急设施", "参加安全教育培训和应急演练，熟悉岗位应急处置卡", "及时上报身边的事故隐患，有权拒绝违章指挥和强令冒险作业"]', '张三（安全员）', NOW(), NOW()),
(9902, 1, 9712, '["贯彻执行安全生产法律法规和企业安全管理制度", "组织开展日常安全检查和隐患排查，督促隐患按期整改", "审批危险作业票，监督作业现场安全措施落实", "组织员工安全教育培训和应急演练，建立培训档案", "管理劳动防护用品和职业健康监护工作", "发生事故时立即报告，参与事故调查和原因分析"]', '张三（安全员）', NOW(), NOW());

-- 演示责任书：今年的签署中（张三已签，李四、赵六待签，还有 5 天截止）；去年的已结束并考核
INSERT INTO resp_book (id, tenant_id, year, title, content, deadline, status, publish_time, publisher_name, remark, create_time, update_time) VALUES
(9911, 1, YEAR(CURDATE()), CONCAT(YEAR(CURDATE()), ' 年度安全生产责任书'), '一、本责任书依据《中华人民共和国安全生产法》和公司安全生产责任制制定，责任期为当年 1 月 1 日至 12 月 31 日。
二、责任人应认真履行本岗位安全生产职责（见下方“岗位安全职责”），做到“不伤害自己、不伤害他人、不被他人伤害、保护他人不受伤害”。
三、年度安全目标：不发生一般及以上生产安全事故；隐患整改率 100%；特种作业持证上岗率 100%；安全培训合格率 100%。
四、公司年底对责任落实情况进行考核，考核结果与年度绩效挂钩。
五、本责任书经责任人手写签名后生效。', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'PUBLISHED',
 DATE_SUB(NOW(), INTERVAL 10 DAY), '张三（安全员）', NULL, DATE_SUB(NOW(), INTERVAL 11 DAY), NOW()),
(9912, 1, YEAR(CURDATE()) - 1, CONCAT(YEAR(CURDATE()) - 1, ' 年度安全生产责任书'), '一、本责任书依据《中华人民共和国安全生产法》和公司安全生产责任制制定，责任期为当年 1 月 1 日至 12 月 31 日。
二、责任人应认真履行本岗位安全生产职责（见下方“岗位安全职责”），做到“不伤害自己、不伤害他人、不被他人伤害、保护他人不受伤害”。
三、年度安全目标：不发生一般及以上生产安全事故；隐患整改率 100%；特种作业持证上岗率 100%；安全培训合格率 100%。
四、公司年底对责任落实情况进行考核，考核结果与年度绩效挂钩。
五、本责任书经责任人手写签名后生效。', DATE_SUB(CURDATE(), INTERVAL 300 DAY), 'CLOSED',
 DATE_SUB(NOW(), INTERVAL 320 DAY), '张三（安全员）', NULL, DATE_SUB(NOW(), INTERVAL 321 DAY), NOW());

INSERT INTO resp_sign (id, tenant_id, book_id, user_id, user_name, posts, duties, status, sign_time, sign_image, sign_hash,
                       score, grade, assess_comment, assessor_name, assess_time, version, create_time, update_time) VALUES
(9921, 1, 9911, 102, '张三（安全员）', '安全管理人员', '["贯彻执行安全生产法律法规和企业安全管理制度", "组织开展日常安全检查和隐患排查，督促隐患按期整改", "审批危险作业票，监督作业现场安全措施落实", "组织员工安全教育培训和应急演练，建立培训档案", "管理劳动防护用品和职业健康监护工作", "发生事故时立即报告，参与事故调查和原因分析"]', 'SIGNED', DATE_SUB(NOW(), INTERVAL 8 DAY), 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', '0413b62f7fa7c53b539eccb2a63fb39eb6dabc427aa15fc7bb6c9c345664e103',
 NULL, NULL, NULL, NULL, NULL, 1, NOW(), NOW()),
(9922, 1, 9911, 103, '李四（作业负责人）', '罐区操作工', '["严格执行罐区操作规程和交接班制度，认真填写运行记录", "按规定巡检储罐液位、温度、压力和呼吸阀，发现异常立即报告", "进入受限空间、动火等危险作业前必须办理作业票，未经批准不作业", "正确佩戴和使用劳动防护用品，爱护消防器材和应急设施", "参加安全教育培训和应急演练，熟悉岗位应急处置卡", "及时上报身边的事故隐患，有权拒绝违章指挥和强令冒险作业"]', 'PENDING', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW(), NOW()),
(9923, 1, 9911, 104, '赵六（监护人）', '罐区操作工', '["严格执行罐区操作规程和交接班制度，认真填写运行记录", "按规定巡检储罐液位、温度、压力和呼吸阀，发现异常立即报告", "进入受限空间、动火等危险作业前必须办理作业票，未经批准不作业", "正确佩戴和使用劳动防护用品，爱护消防器材和应急设施", "参加安全教育培训和应急演练，熟悉岗位应急处置卡", "及时上报身边的事故隐患，有权拒绝违章指挥和强令冒险作业"]', 'PENDING', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW(), NOW()),
(9924, 1, 9912, 102, '张三（安全员）', '安全管理人员', '["贯彻执行安全生产法律法规和企业安全管理制度", "组织开展日常安全检查和隐患排查，督促隐患按期整改", "审批危险作业票，监督作业现场安全措施落实", "组织员工安全教育培训和应急演练，建立培训档案", "管理劳动防护用品和职业健康监护工作", "发生事故时立即报告，参与事故调查和原因分析"]', 'SIGNED', DATE_SUB(NOW(), INTERVAL 315 DAY), 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', '0413b62f7fa7c53b539eccb2a63fb39eb6dabc427aa15fc7bb6c9c345664e103',
 95, '优秀', '隐患排查组织得力，全年按期完成检查任务', '演示企业管理员', DATE_SUB(NOW(), INTERVAL 5 DAY), 2, NOW(), NOW()),
(9925, 1, 9912, 103, '李四（作业负责人）', '罐区操作工', '["严格执行罐区操作规程和交接班制度，认真填写运行记录", "按规定巡检储罐液位、温度、压力和呼吸阀，发现异常立即报告", "进入受限空间、动火等危险作业前必须办理作业票，未经批准不作业", "正确佩戴和使用劳动防护用品，爱护消防器材和应急设施", "参加安全教育培训和应急演练，熟悉岗位应急处置卡", "及时上报身边的事故隐患，有权拒绝违章指挥和强令冒险作业"]', 'SIGNED', DATE_SUB(NOW(), INTERVAL 298 DAY), 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', '0413b62f7fa7c53b539eccb2a63fb39eb6dabc427aa15fc7bb6c9c345664e103',
 82, '良好', '作业票规范；有 1 次逾期签署责任书', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 5 DAY), 2, NOW(), NOW()),
(9926, 1, 9912, 104, '赵六（监护人）', '罐区操作工', '["严格执行罐区操作规程和交接班制度，认真填写运行记录", "按规定巡检储罐液位、温度、压力和呼吸阀，发现异常立即报告", "进入受限空间、动火等危险作业前必须办理作业票，未经批准不作业", "正确佩戴和使用劳动防护用品，爱护消防器材和应急设施", "参加安全教育培训和应急演练，熟悉岗位应急处置卡", "及时上报身边的事故隐患，有权拒绝违章指挥和强令冒险作业"]', 'SIGNED', DATE_SUB(NOW(), INTERVAL 310 DAY), 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', '0413b62f7fa7c53b539eccb2a63fb39eb6dabc427aa15fc7bb6c9c345664e103',
 NULL, NULL, NULL, NULL, NULL, 1, NOW(), NOW());
