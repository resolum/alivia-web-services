-- Appointments are scheduled from a configuration draft, whose current frontend does not collect
-- contact details. The domain model already treats these fields as optional.
ALTER TABLE appointments
    ALTER COLUMN contact_name DROP NOT NULL,
    ALTER COLUMN contact_phone DROP NOT NULL;

-- A draft can have more than one appointment during its lifecycle (evaluation and installation).
-- Service-level checks keep one scheduled appointment of each type for the draft.
ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS uk_appointments_draft;
