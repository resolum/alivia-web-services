ALTER TABLE users
    ADD COLUMN code_failed_attempts integer NOT NULL DEFAULT 0;
