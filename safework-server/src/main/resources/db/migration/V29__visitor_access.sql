-- 第三十四部分：外来人员与车辆入厂（预约 → 危化品车辆审批 → 门岗核对证件、手写签《入厂安全须知》→ 入厂 → 离厂；在厂清单用于应急清点）

CREATE TABLE visit_pass (
    id                      BIGINT        NOT NULL COMMENT '主键',
    tenant_id               BIGINT        NOT NULL COMMENT '企业',
    pass_no                 VARCHAR(20)   NOT NULL,
    visit_type              VARCHAR(10)   NOT NULL COMMENT 'VISITOR 访客 / VEHICLE 普通车辆 / HAZMAT 危化品运输车辆',
    visitor_name            VARCHAR(30)   NOT NULL COMMENT '来访人（车辆为驾驶员）',
    phone                   VARCHAR(20)   NOT NULL,
    id_masked               VARCHAR(20)   NOT NULL COMMENT '身份证号（打码，只存前 3 后 4）',
    company                 VARCHAR(60)   NOT NULL,
    purpose                 VARCHAR(100)  NOT NULL,
    people_count            INT           NOT NULL DEFAULT 1,
    plate_no                VARCHAR(10)   NULL,
    host_id                 BIGINT        NOT NULL COMMENT '接待人',
    host_name               VARCHAR(50)   NOT NULL,
    creator_id              BIGINT        NOT NULL,
    creator_name            VARCHAR(50)   NOT NULL,
    plan_date               DATE          NOT NULL,
    status                  VARCHAR(10)   NOT NULL COMMENT 'PENDING / APPROVED / ENTERED / LEFT / REJECTED / CANCELLED / EXPIRED',
    driver_cert_no          VARCHAR(30)   NULL COMMENT '驾驶员道路危险货物运输从业资格证',
    driver_cert_expire      DATE          NULL,
    escort_name             VARCHAR(30)   NULL COMMENT '押运员',
    escort_cert_no          VARCHAR(30)   NULL,
    escort_cert_expire      DATE          NULL,
    transport_permit_no     VARCHAR(40)   NULL COMMENT '车辆道路运输证',
    transport_permit_expire DATE          NULL,
    cargo_name              VARCHAR(40)   NULL,
    cargo_un_no             VARCHAR(10)   NULL,
    cargo_qty               DOUBLE        NULL COMMENT '吨',
    destination             VARCHAR(40)   NULL COMMENT '卸货地点',
    approver_name           VARCHAR(50)   NULL,
    approve_time            DATETIME      NULL,
    reject_reason           VARCHAR(100)  NULL,
    sign_image              MEDIUMTEXT    NULL COMMENT '入厂安全须知手写签名 PNG data URL',
    sign_hash               CHAR(64)      NULL,
    sign_time               DATETIME      NULL,
    enter_time              DATETIME      NULL,
    enter_guard             VARCHAR(50)   NULL,
    leave_time              DATETIME      NULL,
    leave_guard             VARCHAR(50)   NULL,
    remark                  VARCHAR(200)  NULL,
    create_time             DATETIME      NULL,
    update_time             DATETIME      NULL,
    deleted                 TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_date (tenant_id, plan_date, status),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_creator (tenant_id, creator_id),
    KEY idx_host (tenant_id, host_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外来人员与车辆入厂';

-- 菜单：安全管理下面加【外来人员入厂】；所有员工都能给自己的访客预约，门岗和审批是按钮权限
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, scope, path, component, perms, icon, sort, create_time, update_time) VALUES
(321,  3,   '外来人员入厂', 'C', 'T', '/safety/visit', 'safety/visit/index', 'visit:list', 'visit', 18, NOW(), NOW()),
(3211, 321, '门岗登记', 'F', 'T', NULL, NULL, 'visit:gate', NULL, 1, NOW(), NOW()),
(3212, 321, '危化品车辆审批', 'F', 'T', NULL, NULL, 'visit:approve', NULL, 2, NOW(), NOW());

UPDATE sys_tenant_package SET menu_ids = CONCAT(menu_ids, ',321,3211,3212')
WHERE FIND_IN_SET('300', menu_ids) > 0 AND FIND_IN_SET('321', menu_ids) = 0;

-- 安全员：全部；监护人：预约 + 门岗；作业负责人：预约
INSERT INTO sys_role_menu (id, tenant_id, role_id, menu_id)
SELECT UUID_SHORT(), r.tenant_id, r.id, m.id
FROM sys_role r JOIN sys_menu m
  ON (r.role_code = 'safety' AND m.id IN (321, 3211, 3212))
  OR (r.role_code = 'guardian' AND m.id IN (321, 3211))
  OR (r.role_code = 'leader' AND m.id = 321)
WHERE r.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ===== 演示数据（日期相对今天）=====
-- 今天：1 位访客待入厂、1 位访客在厂（已签须知）、1 辆甲醇槽车在厂卸车、1 辆液碱车驾驶员资格证过期（门岗拦下）、1 辆送货车待入厂
-- 明天：1 辆乙酸乙酯车待审批；昨天入厂没登记离厂的 1 人（超时提醒）；过去 3 周的进出记录
INSERT INTO visit_pass (id, tenant_id, pass_no, visit_type, visitor_name, phone, id_masked, company, purpose, people_count, plate_no,
                        host_id, host_name, creator_id, creator_name, plan_date, status,
                        driver_cert_no, driver_cert_expire, escort_name, escort_cert_no, escort_cert_expire, transport_permit_no, transport_permit_expire,
                        cargo_name, cargo_un_no, cargo_qty, destination, approver_name, approve_time, reject_reason,
                        sign_image, sign_hash, sign_time, enter_time, enter_guard, leave_time, leave_guard, remark, create_time, update_time) VALUES
(9901, 1, CONCAT('RC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-9901'), 'VISITOR', '王建国', '13905510001', '340***********2318', '合肥通用机械有限公司', 'R-201 合成反应釜年度检修技术交流', 2, NULL,
 103, '李四（作业负责人）', 103, '李四（作业负责人）', CURDATE(), 'APPROVED',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9902, 1, CONCAT('RC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-9902'), 'VISITOR', '陈明', '13866120002', '340***********4527', '安徽某安全技术咨询有限公司', '安全生产标准化复评预审', 1, NULL,
 102, '张三（安全员）', 102, '张三（安全员）', CURDATE(), 'ENTERED',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', '0413b62f7fa7c53b539eccb2a63fb39eb6dabc427aa15fc7bb6c9c345664e103', TIMESTAMP(CURDATE(), '09:05:00'), TIMESTAMP(CURDATE(), '09:06:00'), '赵六（监护人）', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
(9903, 1, CONCAT('RC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-9903'), 'HAZMAT', '孙卫东', '13705510003', '342***********1856', '安徽某危险品运输有限公司', '甲醇卸车（罐区 2# 储罐）', 2, '皖A7J781',
 102, '张三（安全员）', 103, '李四（作业负责人）', CURDATE(), 'ENTERED',
 '340100198612070031', DATE_ADD(CURDATE(), INTERVAL 2 YEAR), '周海', '340100199003150042', DATE_ADD(CURDATE(), INTERVAL 1 YEAR), '皖交运管合字 340100018832', DATE_ADD(CURDATE(), INTERVAL 3 YEAR),
 '甲醇', 'UN1230', 28, '罐区装卸站', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL,
 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', '0413b62f7fa7c53b539eccb2a63fb39eb6dabc427aa15fc7bb6c9c345664e103', TIMESTAMP(CURDATE(), '08:38:00'), TIMESTAMP(CURDATE(), '08:40:00'), '赵六（监护人）', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW()),
(9904, 1, CONCAT('RC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-9904'), 'HAZMAT', '钱国强', '13955110004', '341***********0619', '某化工物流有限公司', '液碱卸车（污水站加药间）', 2, '皖B2K665',
 102, '张三（安全员）', 103, '李四（作业负责人）', CURDATE(), 'APPROVED',
 '341200198105120056', DATE_SUB(CURDATE(), INTERVAL 10 DAY), '吴强', '341200198809220013', DATE_ADD(CURDATE(), INTERVAL 8 MONTH), '皖交运管阜字 341200006671', DATE_ADD(CURDATE(), INTERVAL 1 YEAR),
 '氢氧化钠溶液（液碱）', 'UN1824', 15, '污水站加药间', '张三（安全员）', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, '审批时驾驶员资格证还在有效期内', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(9905, 1, CONCAT('RC', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-9905'), 'VEHICLE', '马师傅', '13605510005', '340***********3372', '合肥某劳保用品商行', '送劳保用品（防毒面具滤毒罐）', 1, '皖A5M218',
 102, '张三（安全员）', 102, '张三（安全员）', CURDATE(), 'APPROVED',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '仓库', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9906, 1, CONCAT('RC', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '-9906'), 'HAZMAT', '郑立新', '13805510006', '340***********5541', '安徽某危险品运输有限公司', '乙酸乙酯卸车（罐区 3# 储罐）', 2, '皖A8P902',
 102, '张三（安全员）', 103, '李四（作业负责人）', DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'PENDING',
 '340100197909280017', DATE_ADD(CURDATE(), INTERVAL 1 YEAR), '冯军', '340100198512010026', DATE_ADD(CURDATE(), INTERVAL 2 YEAR), '皖交运管合字 340100019905', DATE_ADD(CURDATE(), INTERVAL 2 YEAR),
 '乙酸乙酯', 'UN1173', 20, '罐区装卸站', NULL, NULL, NULL,
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NOW(), NOW()),
(9907, 1, CONCAT('RC', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '-9907'), 'VISITOR', '刘工', '15905510007', '340***********7713', '某自控工程有限公司', 'SIS 紧急切断阀调试', 2, NULL,
 103, '李四（作业负责人）', 103, '李四（作业负责人）', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'ENTERED',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAPAAAABQCAYAAAAnSfh8AAADVklEQVR42u3dXU7sMAyG4SliCd3/CmcPcHUkjsSUTJofO3neSxClSf3FjmO3jwcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAMjKeZ5fZgEr8LGreIkYBMzzAgQ8iufzeRA0CJhXBgg4ghcmYuMn4AVEvLt4z/P8ImQCth9O7nmJOBefpuB/483knf8S29VYCJUHFkoH37e+Cot/+9nPuSDuPNgL/mKw0YXdWmD/xvvzuvIEOSIyAi7wSNGFeydUvsoF7CriTAv6B/nmOFqqEe9fv+dl8yf2CDiBMb+KEEru+V3j23Uv/CpfUDrPs87ZrcDBw6c74X2J4by61i6h9NUctRh373nkgQNXKbUWryq08m1Ja/H2mmsCDhpK9xJv6TVWDqV7hsujE6MEHLBKq7fn3bUK7apctPfC3ev6BBwslB4RNtf8bXaR9w6XZ+VRCDj40VIv8e5yhDQiXJ5pIwQcaD9cu3LXet6SUDqrF54RLs8oCCLgIPvh2uu2NpoVRDwyXJ5dzfdpT/v+Ct26Rrb24bcwmufzeaySxJqVoJr5Pw/CrTtKmX3c0Nvz/pUDiLSH7l2MEbn45yDaum6dFg+qRoS9vEzJvUQQcOmzHHV/sxthjtXbsFp6uJaGEkm8GbxwzSLc+/4idLF1TaNnriNuUU/cW7wjjkFme+EW+/NRx0Uz7P1YcVA9PFWrfVZk8UYJpe90UI2IViL1jy+3Mo0OM3uHvzPmc2YoXZtkjHLMtsQbOWZl5WZ7qtnVVdlD6dbibT2PEd/ccqySWo/gqSIaXZZQekbScKYnTyfg1UrYoiW5ZsxrKwH3mMPedcpRTlmO7AfcGTxV5jCtp4hnnulGPJYKWcjRU8RZPFX2t2NePcNaAY/yulmqudIIeMVUfouFKvqrbVuK+M4XJfAY243Uo682orHf6VrK8l7qV+1673QrEW/CdsKWIs5k7CXGnGU8JW+vvBumEm/wZoa7xprB2EtD6Uitb62e41UonXm8BBywfS5KEURWQ45ao0zAwUScceW+Gt8K4r0rYsJdoB94lXO4LH2rGQtaELyhf3Uj3zXjulKUQcCbP/SdkzcZvq9LwOCNgB1EvPOHxAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgN74BhomdDoiWN6oAAAAASUVORK5CYII=', '0413b62f7fa7c53b539eccb2a63fb39eb6dabc427aa15fc7bb6c9c345664e103', TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '13:20:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '13:22:00'), '赵六（监护人）', NULL, NULL, '晚上加班调试，离厂时没有登记', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 过去 3 周的进出记录（已离厂）
INSERT INTO visit_pass (id, tenant_id, pass_no, visit_type, visitor_name, phone, id_masked, company, purpose, people_count, plate_no,
                        host_id, host_name, creator_id, creator_name, plan_date, status, cargo_name, cargo_un_no, cargo_qty, destination,
                        driver_cert_no, driver_cert_expire, escort_name, escort_cert_no, escort_cert_expire, transport_permit_no, transport_permit_expire,
                        approver_name, approve_time, sign_time, enter_time, enter_guard, leave_time, leave_guard, create_time, update_time)
WITH RECURSIVE seq (n) AS (SELECT 2 UNION ALL SELECT n + 1 FROM seq WHERE n < 21)
SELECT 9910 + n, 1, CONCAT('RC', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL n DAY), '%Y%m%d'), '-', 9910 + n),
       IF(n % 3 = 0, 'HAZMAT', 'VISITOR'),
       ELT(1 + n % 5, '孙卫东', '赵敏', '李华', '周建', '吴峰'), CONCAT('139055100', LPAD(n, 2, '0')), '340***********1100',
       IF(n % 3 = 0, '安徽某危险品运输有限公司', ELT(1 + n % 4, '合肥通用机械有限公司', '某消防器材有限公司', '某自控工程有限公司', '某环保检测有限公司')),
       IF(n % 3 = 0, '甲醇卸车（罐区）', ELT(1 + n % 4, '设备维保', '消防器材年检', '仪表调试', '废气检测采样')),
       IF(n % 3 = 0, 2, 1), IF(n % 3 = 0, '皖A7J781', NULL),
       102, '张三（安全员）', 102, '张三（安全员）', DATE_SUB(CURDATE(), INTERVAL n DAY), 'LEFT',
       IF(n % 3 = 0, '甲醇', NULL), IF(n % 3 = 0, 'UN1230', NULL), IF(n % 3 = 0, 28, NULL), IF(n % 3 = 0, '罐区装卸站', NULL),
       IF(n % 3 = 0, '340100198612070031', NULL), IF(n % 3 = 0, DATE_ADD(CURDATE(), INTERVAL 2 YEAR), NULL),
       IF(n % 3 = 0, '周海', NULL), IF(n % 3 = 0, '340100199003150042', NULL), IF(n % 3 = 0, DATE_ADD(CURDATE(), INTERVAL 1 YEAR), NULL),
       IF(n % 3 = 0, '皖交运管合字 340100018832', NULL), IF(n % 3 = 0, DATE_ADD(CURDATE(), INTERVAL 3 YEAR), NULL),
       IF(n % 3 = 0, '张三（安全员）', NULL), IF(n % 3 = 0, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n + 1 DAY), '16:00:00'), NULL),
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '08:55:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '09:00:00'), '赵六（监护人）',
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), IF(n % 3 = 0, '10:40:00', '16:30:00')), '赵六（监护人）',
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n + 1 DAY), '10:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL n DAY), '16:30:00')
FROM seq WHERE n % 4 <> 1;
