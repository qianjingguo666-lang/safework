ALTER TABLE work_ticket ADD COLUMN acceptance_reviewer_id BIGINT NULL,
    ADD INDEX ix_ticket_acceptance (tenant_id, acceptance_reviewer_id, status);
