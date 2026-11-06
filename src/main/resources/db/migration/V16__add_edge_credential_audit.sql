-- V16: who issued and who revoked each edge credential, and when the current secret was issued (AP-68, H-06).
--
-- issued_at is when the current secret was issued or rotated (it lets the API say how old a credential is).
-- updated_at cannot stand for it: it also changes when the credential is revoked. Rows that already exist take
-- updated_at, the closest thing known, so their age is an estimate (never older than the real one).
-- issued_by is the account of the administrator who issued the secret; it stays empty for those older rows.
-- revoked_by is the administrator who revoked it; it is empty when a change of the edge revoked it (it was removed
-- from its installation, retired, replaced or changed its type) and revoked_reason says which one.
--
-- IF NOT EXISTS keeps databases where ddl-auto=update already created the columns from failing.

ALTER TABLE edge_credentials ADD COLUMN IF NOT EXISTS issued_at timestamp(6) without time zone;
ALTER TABLE edge_credentials ADD COLUMN IF NOT EXISTS issued_by bigint;
ALTER TABLE edge_credentials ADD COLUMN IF NOT EXISTS revoked_by bigint;
ALTER TABLE edge_credentials ADD COLUMN IF NOT EXISTS revoked_reason character varying(40);

UPDATE edge_credentials SET issued_at = updated_at WHERE issued_at IS NULL;

ALTER TABLE edge_credentials ALTER COLUMN issued_at SET NOT NULL;
