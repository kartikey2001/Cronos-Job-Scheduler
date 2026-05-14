-- ── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50)  UNIQUE NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- ── Jobs ─────────────────────────────────────────────────────────────────────
CREATE TABLE jobs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    webhook_url     TEXT         NOT NULL,
    cron_expression VARCHAR(100),
    scheduled_at    TIMESTAMP    NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                                 CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','DEAD','CANCELLED')),
    max_retries     INT          NOT NULL DEFAULT 3,
    retry_count     INT          NOT NULL DEFAULT 0,
    retry_delay_ms  BIGINT       NOT NULL DEFAULT 5000,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_jobs_user_id             ON jobs (user_id);
CREATE INDEX idx_jobs_status_scheduled_at ON jobs (status, scheduled_at);

-- ── Execution logs ────────────────────────────────────────────────────────────
CREATE TABLE execution_logs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID        NOT NULL REFERENCES jobs(id),
    execution_id UUID        NOT NULL,
    status       VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    attempt      INT         NOT NULL,
    output       TEXT,
    error_message TEXT,
    started_at   TIMESTAMP   NOT NULL,
    finished_at  TIMESTAMP
);

-- ── Dead-letter queue ─────────────────────────────────────────────────────────
CREATE TABLE dead_letter_queue (
    id             UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id         UUID      NOT NULL REFERENCES jobs(id),
    failure_reason TEXT,
    moved_at       TIMESTAMP NOT NULL DEFAULT now()
);
