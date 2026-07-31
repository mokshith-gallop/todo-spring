-- V2: Create the priority ENUM type and task table.

-- ── Priority ENUM type ─────────────────────────────────
DO $$ BEGIN
    CREATE TYPE priority AS ENUM ('none', 'low', 'med', 'high');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ── task table ─────────────────────────────────────────
CREATE TABLE task (
    id            UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID            NOT NULL REFERENCES user_account(id),
    list_id       UUID            NOT NULL,
    title         VARCHAR(500)    NOT NULL,
    notes         TEXT,
    priority      priority        NOT NULL DEFAULT 'none',
    position      DOUBLE PRECISION NOT NULL,
    due_at        TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    version       INTEGER         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,

    -- Composite FK: ensures list belongs to the same user (tenant safety)
    CONSTRAINT fk_task_list_user
        FOREIGN KEY (list_id, user_id) REFERENCES task_list(id, user_id),

    -- Title must not be blank after trimming
    CONSTRAINT ck_task_title_not_blank
        CHECK (length(trim(title)) > 0),

    -- Title max length (defence in depth — also enforced by VARCHAR(500))
    CONSTRAINT ck_task_title_length
        CHECK (length(title) <= 500),

    -- Notes max length
    CONSTRAINT ck_task_notes_length
        CHECK (notes IS NULL OR length(notes) <= 10000)
);

-- ── Indexes ────────────────────────────────────────────

-- Serves ordered listing within a list + MAX(position) on append
CREATE INDEX ix_task_list_position
    ON task (list_id, position)
    WHERE deleted_at IS NULL;

-- Serves tenant-scoped queries (list all user's tasks)
CREATE INDEX ix_task_user_id
    ON task (user_id)
    WHERE deleted_at IS NULL;

-- Supports due-date filtering (future story)
CREATE INDEX ix_task_due_at
    ON task (user_id, due_at)
    WHERE deleted_at IS NULL AND due_at IS NOT NULL;
