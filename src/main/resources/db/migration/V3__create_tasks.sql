CREATE TABLE tasks (
    id           BIGSERIAL     PRIMARY KEY,
    title        VARCHAR(120)  NOT NULL,
    description  VARCHAR(2000),
    status       VARCHAR(20)   NOT NULL DEFAULT 'TODO',
    -- stored as a level (1 = LOW, 2 = MEDIUM, 3 = HIGH) so that sorting by priority is meaningful
    priority     INTEGER       NOT NULL DEFAULT 2,
    due_date     DATE,
    completed_at TIMESTAMPTZ,
    owner_id     BIGINT        NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_tasks_status   CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    CONSTRAINT ck_tasks_priority CHECK (priority BETWEEN 1 AND 3)
);

CREATE INDEX idx_tasks_owner_status ON tasks (owner_id, status);
