-- V7: an installation belongs to a household, and an assignment remembers its installation
-- (AP-376 / P-376.2).
--
-- device_networks is the network of IoT devices of one installation. The business (Alivia) owns the
-- devices; the household is the one the installation was done for. household_id is a plain number:
-- the household lives in Profiles and no foreign key crosses contexts.
--
-- The new columns are nullable because the tables already exist; the application requires them for every
-- new row. No released environment holds rows of these tables (they were created in V5 and V6).
--
-- Two rules are enforced by the database:
--   * one network per installation (uk_device_networks_installation);
--   * one ACTIVE network per household. The assignments of a household are serialized by locking that
--     network, which only works when there is a single one. This is a partial unique index, which
--     Hibernate cannot declare from the entity.

ALTER TABLE device_networks
    ADD COLUMN IF NOT EXISTS household_id bigint;

ALTER TABLE device_assignments
    ADD COLUMN IF NOT EXISTS installation_id bigint;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_device_networks_installation') THEN
        ALTER TABLE device_networks
            ADD CONSTRAINT uk_device_networks_installation UNIQUE (installation_id);
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_networks_active_household
    ON device_networks (household_id)
    WHERE network_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_device_assignments_installation_active
    ON device_assignments (installation_id)
    WHERE unassigned_at IS NULL;
