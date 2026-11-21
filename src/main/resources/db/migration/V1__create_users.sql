CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    full_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(100) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- emails are stored lowercase, so a plain unique constraint is case-insensitive
    CONSTRAINT uk_users_email UNIQUE (email)
);
