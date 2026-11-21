-- Seeds the single ADMIN account. Credentials come from the ADMIN_EMAIL and
-- ADMIN_PASSWORD environment variables (Flyway placeholders). pgcrypto produces a
-- standard BCrypt ($2a$) hash that Spring Security's BCryptPasswordEncoder accepts.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (full_name, email, password, role, active, created_at)
VALUES ('Administrator',
        lower(trim('${admin_email}')),
        crypt('${admin_password}', gen_salt('bf', 10)),
        'ADMIN',
        TRUE,
        now());
