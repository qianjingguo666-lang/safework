-- 第三十七部分：安全风险研判与承诺公告（危险化学品企业安全风险研判与承诺公告制度）
-- 每天生产、设备、工艺、安全负责人逐项研判（9:00 前），主要负责人手写签名承诺并公告（10:00 前）

CREATE TABLE risk_commitment (
    id                 BIGINT        NOT NULL COMMENT '主键',
    tenant_id          BIGINT        NOT NULL COMMENT '企业',
    commit_date        DATE          NOT NULL,
    status             VARCHAR(10)   NOT NULL COMMENT 'JUDGING 研判中 / COMMITTED 已承诺',
    units_total        INT           NULL COMMENT '生产装置总数',
    units_running      INT           NULL,
    units_stopped      INT           NULL,
    units_overhaul     INT           NULL,
    trial_run          TINYINT       NULL COMMENT '是否试生产',
    startup_shutdown   TINYINT       NULL COMMENT '是否有开停车',
    condition_note     VARCHAR(200)  NULL,
    hot_work_count     INT           NULL COMMENT '承诺时统计：今天的动火作业票',
    confined_count     INT           NULL,
    height_count       INT           NULL,
    major_sources      INT           NULL COMMENT '重大危险源',
    overstock          INT           NULL COMMENT '超储',
    open_major_hazards INT           NULL,
    open_hazards       INT           NULL,
    moc_temporary      INT           NULL COMMENT '未恢复的临时变更',
    abnormal_count     INT           NOT NULL DEFAULT 0,
    content            VARCHAR(300)  NULL COMMENT '承诺内容',
    committer_id       BIGINT        NULL,
    committer_name     VARCHAR(50)   NULL,
    commit_time        DATETIME      NULL,
    sign_image         MEDIUMTEXT    NULL COMMENT '主要负责人手写签名 PNG data URL',
    sign_hash          CHAR(64)      NULL,
    create_time        DATETIME      NULL,
    update_time        DATETIME      NULL,
    deleted            TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_date (tenant_id, commit_date, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全风险研判与承诺';

CREATE TABLE risk_judgment (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '企业',
    commitment_id  BIGINT        NOT NULL,
    aspect         VARCHAR(12)   NOT NULL COMMENT 'PRODUCTION / EQUIPMENT / PROCESS / SAFETY',
    judger_id      BIGINT        NOT NULL,
    judger_name    VARCHAR(50)   NOT NULL,
    judge_time     DATETIME      NOT NULL,
    abnormal_count INT           NOT NULL DEFAULT 0,
    items          TEXT          NOT NULL COMMENT '逐项结果 JSON',
    create_time    DATETIME      NULL,
    update_time    DATETIME      NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commitment_aspect (commitment_id, aspect, deleted),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全风险研判';

-- 菜单：安全管理下面加【风险研判承诺】；作业负责人、监护人都能看公告；安全员、作业负责人能研判；承诺签字给企业管理员（主要负责人）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(323,  3,   '风险研判承诺', 'C', 'T', '/safety/commit', 'safety/commit/index', 'commit:list', 'commit', 0, NOW(), NOW()),
(3231, 323, '研判', 'F', 'T', NULL, NULL, 'commit:judge', NULL, 1, NOW(), NOW()),
(3232, 323, '承诺签字', 'F', 'T', NULL, NULL, 'commit:sign', NULL, 2, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',323,3231,3232')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('323', menu_ids) = 0;

INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m
  ON (r.role_code IN ('safety', 'leader') AND m.id IN (323, 3231))
  OR (r.role_code = 'guardian' AND m.id = 323)
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ===== 演示数据（日期相对今天）=====
-- 过去 30 天：28 天按时承诺；12 天前 10:40 才承诺（超时）；18 天前漏了；3 天前设备研判有 1 项异常、8 天前安全研判有 1 项异常
-- 今天：生产（李四）、安全（张三）已研判，设备、工艺还没研判
INSERT INTO risk_commitment (id, tenant_id, commit_date, status, units_total, units_running, units_stopped, units_overhaul, trial_run, startup_shutdown,
                             hot_work_count, confined_count, height_count, major_sources, overstock, open_major_hazards, open_hazards, moc_temporary,
                             abnormal_count, content, committer_id, committer_name, commit_time, sign_image, sign_hash, create_time, update_time)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT 990000 + n, 1, DATE_SUB(CURDATE(), INTERVAL n DAY), 'COMMITTED', 6, IF(n % 7 = 0, 4, 5), 0, IF(n % 7 = 0, 2, 1), 0, IF(n = 5, 1, 0),
       n % 3, IF(n % 4 = 0, 1, 0), IF(n % 5 = 0, 1, 0), 2, 0, IF(n = 8, 1, 0), 3 + n % 3, 1,
       IF(n IN (3, 8), 1, 0),
       IF(n IN (3, 8), '今天我公司已进行安全风险研判，发现 1 项异常情况，已逐项落实管控措施，我承诺在管控措施落实到位的前提下组织生产，罐区、仓库等重大危险源安全风险得到有效管控。',
                       '今天我公司已进行安全风险研判，各项安全风险防控措施已落实到位，我承诺所有生产装置处于安全运行状态，罐区、仓库等重大危险源安全风险得到有效管控。'),
       101, '演示企业管理员', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), IF(n = 12, '10:40:00', CONCAT('09:', LPAD(20 + n, 2, '0'), ':00'))),
       'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', SHA2('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', 256),
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '08:05:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '09:30:00')
FROM seq WHERE n <> 18 AND NOT EXISTS (SELECT 1 FROM risk_commitment c WHERE c.id = 990000 + n);

INSERT INTO risk_commitment (id, tenant_id, commit_date, status, units_total, units_running, units_stopped, units_overhaul, trial_run, startup_shutdown,
                             abnormal_count, create_time, update_time)
SELECT 990000, 1, CURDATE(), 'JUDGING', 6, 5, 0, 1, 0, 0, 0, TIMESTAMP(CURDATE(), '08:05:00'), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM risk_commitment c WHERE c.id = 990000);

INSERT INTO risk_judgment (id, tenant_id, commitment_id, aspect, judger_id, judger_name, judge_time, abnormal_count, items, create_time, update_time)
SELECT c.id * 10 + a.k, 1, c.id, a.aspect, a.uid, a.uname,
       TIMESTAMP(c.commit_date, CONCAT('08:', LPAD(5 + a.k * 7, 2, '0'), ':00')),
       IF((c.id = 990003 AND a.aspect = 'EQUIPMENT') OR (c.id = 990008 AND a.aspect = 'SAFETY'), 1, 0),
       CASE WHEN c.id = 990003 AND a.aspect = 'EQUIPMENT' THEN '[{"code": "E1", "ok": true, "note": null, "measure": null}, {"code": "E2", "ok": true, "note": null, "measure": null}, {"code": "E3", "ok": false, "note": "罐区 2# 可燃气体探头故障", "measure": "已更换备用探头，修好前每 2 小时人工检测一次"}, {"code": "E4", "ok": true, "note": null, "measure": null}]'
            WHEN c.id = 990008 AND a.aspect = 'SAFETY' THEN '[{"code": "S1", "ok": true, "note": null, "measure": null}, {"code": "S2", "ok": false, "note": "污水站有 1 项重大隐患还在整改", "measure": "已设警戒区，整改期间禁止进入，每班巡查"}, {"code": "S3", "ok": true, "note": null, "measure": null}, {"code": "S4", "ok": true, "note": null, "measure": null}]'
            ELSE a.items END,
       NOW(), NOW()
FROM risk_commitment c
JOIN (
    SELECT 1 AS k, 'PRODUCTION' AS aspect, 103 AS uid, '李四（作业负责人）' AS uname, '[{"code": "P1", "ok": true, "note": null, "measure": null}, {"code": "P2", "ok": true, "note": null, "measure": null}, {"code": "P3", "ok": true, "note": null, "measure": null}, {"code": "P4", "ok": true, "note": null, "measure": null}]' AS items
    UNION ALL SELECT 2, 'EQUIPMENT', 104, '赵六（监护人）', '[{"code": "E1", "ok": true, "note": null, "measure": null}, {"code": "E2", "ok": true, "note": null, "measure": null}, {"code": "E3", "ok": true, "note": null, "measure": null}, {"code": "E4", "ok": true, "note": null, "measure": null}]'
    UNION ALL SELECT 3, 'PROCESS', 103, '李四（作业负责人）', '[{"code": "T1", "ok": true, "note": null, "measure": null}, {"code": "T2", "ok": true, "note": null, "measure": null}, {"code": "T3", "ok": true, "note": null, "measure": null}, {"code": "T4", "ok": true, "note": null, "measure": null}]'
    UNION ALL SELECT 4, 'SAFETY', 102, '张三（安全员）', '[{"code": "S1", "ok": true, "note": null, "measure": null}, {"code": "S2", "ok": true, "note": null, "measure": null}, {"code": "S3", "ok": true, "note": null, "measure": null}, {"code": "S4", "ok": true, "note": null, "measure": null}]'
) a ON c.id BETWEEN 990001 AND 990030 OR (c.id = 990000 AND a.aspect IN ('PRODUCTION', 'SAFETY'))
WHERE c.id BETWEEN 990000 AND 990030
  AND NOT EXISTS (SELECT 1 FROM risk_judgment j WHERE j.id = c.id * 10 + a.k);
