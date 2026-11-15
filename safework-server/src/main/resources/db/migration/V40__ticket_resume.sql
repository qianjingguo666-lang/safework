ALTER TABLE work_ticket ADD COLUMN resume_reviewer_id BIGINT NULL,
    ADD INDEX ix_ticket_resume_review (tenant_id,resume_reviewer_id,status);
