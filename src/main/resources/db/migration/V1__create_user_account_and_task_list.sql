-- V1: Prerequisite tables for the todo application.
-- user_account and task_list are created by prior stories
-- (User Registration, Create a Task List).

-- Enable citext extension for case-insensitive email
CREATE EXTENSION IF NOT EXISTS citext;

-- ── user_account ───────────────────────────────────────
CREATE TABLE user_account (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       CITEXT NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ── task_list ──────────────────────────────────────────
CREATE TABLE task_list (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES user_account(id),
    name        VARCHAR(255) NOT NULL,
    is_inbox    BOOLEAN NOT NULL DEFAULT false,
    position    DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

-- Composite unique for the task table's composite FK target
ALTER TABLE task_list ADD CONSTRAINT uq_task_list_id_user_id UNIQUE (id, user_id);

-- Index for user-scoped list queries
CREATE INDEX ix_task_list_user_id ON task_list (user_id) WHERE deleted_at IS NULL;
