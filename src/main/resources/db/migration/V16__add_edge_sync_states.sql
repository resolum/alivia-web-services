-- V16: what each edge service has confirmed of the table of devices of its household (AP-68, transport towards the edge
-- services).
--
-- One row per edge. applied_version is the highest version of the table that the edge reported as applied (null until
-- it reports one); acked_at is when that version was confirmed; last_seen_at is the last time the edge asked for its
-- table. installation_id is the installation the numbers refer to: when the edge is placed somewhere else its row is
-- discarded, because versions of different installations cannot be compared.
--
-- IF NOT EXISTS keeps databases where ddl-auto=update already created the table from failing.

CREATE TABLE IF NOT EXISTS edge_sync_states (
    edge_id bigint PRIMARY KEY,
    installation_id bigint NOT NULL,
    applied_version bigint,
    acked_at timestamp(6) without time zone,
    last_seen_at timestamp(6) without time zone,
    CONSTRAINT fk_edge_sync_states_edge FOREIGN KEY (edge_id) REFERENCES edges (id),
    CONSTRAINT edge_sync_states_applied_version_check CHECK (applied_version IS NULL OR applied_version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_edge_sync_states_installation ON edge_sync_states (installation_id);
