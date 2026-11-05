-- V8: who closed an assignment and why (AP-373 / P-373.1).
--
-- device_assignments is the history of a device. Unassigning closes the row (unassigned_at) and never
-- deletes it; placing the device again adds a new row. The row now also keeps the account that closed
-- it and the reason, which is free text and mandatory.
--
-- A closed row must carry all three facts and cannot end before it began. This is a CHECK constraint,
-- which Hibernate cannot declare from the entity. The columns are nullable because an active assignment
-- has none of them. No released environment holds closed rows (unassigning did not exist before).
--
-- The index serves the history of one device, most recent first. The foreign key to devices has no other
-- index, and the partial unique index of V6 only covers active rows.

ALTER TABLE device_assignments
    ADD COLUMN IF NOT EXISTS unassigned_by bigint;

ALTER TABLE device_assignments
    ADD COLUMN IF NOT EXISTS unassign_reason character varying(300);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'device_assignments_closed_check') THEN
        ALTER TABLE device_assignments
            ADD CONSTRAINT device_assignments_closed_check
            CHECK (unassigned_at IS NULL
                   OR (unassigned_by IS NOT NULL
                       AND unassign_reason IS NOT NULL
                       AND unassigned_at >= assigned_at));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_device_assignments_device
    ON device_assignments (device_id, assigned_at);
