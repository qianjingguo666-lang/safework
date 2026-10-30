-- =====================================================================
-- V5：工程规范
-- 这是第一个由 Flyway 自动执行的脚本：已有数据库启动后端时会自动执行，不需要再手动导入 SQL
-- =====================================================================

-- 1. 操作日志记录 traceId：用户反馈“操作失败、错误编号 xxx”时，可以直接在日志里搜到这次请求的全部记录
ALTER TABLE sys_oper_log ADD COLUMN trace_id VARCHAR(64) NULL COMMENT '请求链路 ID' AFTER ip;

-- 2. 数据看板按创建时间统计，补上索引（tenant_id 在前：所有查询都带租户条件）
ALTER TABLE work_ticket ADD INDEX idx_tenant_create (tenant_id, create_time);
ALTER TABLE hazard ADD INDEX idx_tenant_create (tenant_id, create_time);
