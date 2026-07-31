-- V3: Tighten task_list.name column and add check constraints.

-- Tighten name column from VARCHAR(255) to VARCHAR(120)
ALTER TABLE task_list ALTER COLUMN name TYPE VARCHAR(120);

-- Name must not be blank after trimming (referenced by AC-2)
ALTER TABLE task_list ADD CONSTRAINT ck_task_list_name_not_blank
    CHECK (length(trim(name)) > 0);

-- Name max length defense-in-depth (also enforced by VARCHAR(120))
ALTER TABLE task_list ADD CONSTRAINT ck_task_list_name_length
    CHECK (length(name) <= 120);
