ALTER TABLE users
    ADD COLUMN password_reset_token_hash character varying(64),
    ADD COLUMN password_reset_expires_at timestamp(6) without time zone,
    ADD COLUMN password_reset_requested_at timestamp(6) without time zone;

ALTER TABLE users
    ADD CONSTRAINT uk_users_password_reset_token_hash UNIQUE (password_reset_token_hash);
