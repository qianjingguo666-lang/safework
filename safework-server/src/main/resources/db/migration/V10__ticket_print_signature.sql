-- 第十五部分：作业票打印与手写签名

-- 手写签名：每条操作记录（提交、审批、开工、完工……）最多一个签名，谁操作谁签
CREATE TABLE ticket_signature (
    id           BIGINT        NOT NULL COMMENT '主键',
    tenant_id    BIGINT        NOT NULL COMMENT '企业',
    ticket_id    BIGINT        NOT NULL,
    log_id       BIGINT        NOT NULL COMMENT '对应的操作记录',
    action       VARCHAR(20)   NOT NULL,
    signer_id    BIGINT        NOT NULL,
    signer_name  VARCHAR(50)   NULL,
    image        MEDIUMTEXT    NOT NULL COMMENT '签名图片（PNG data URL）',
    image_hash   CHAR(64)      NOT NULL COMMENT '图片 SHA-256',
    create_time  DATETIME      NULL,
    update_time  DATETIME      NULL,
    deleted      TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_log (log_id),
    KEY idx_ticket (tenant_id, ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业票手写签名';

-- 纸质作业票上二维码的验真码（第一次打印时生成）
ALTER TABLE work_ticket
    ADD COLUMN verify_token CHAR(32) NULL COMMENT '打印验真码' AFTER flow_snapshot,
    ADD UNIQUE KEY uk_verify_token (verify_token);
