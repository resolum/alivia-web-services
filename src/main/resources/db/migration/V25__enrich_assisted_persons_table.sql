-- V25: Enrich assisted_persons table with care domain fields
--
-- Adds care details to the AssistedPerson aggregate in the Assistance bounded context:
-- household link, kinship, mobility level, emergency contact, and consent timestamp.

ALTER TABLE assisted_persons
    ADD COLUMN IF NOT EXISTS household_id BIGINT,
    ADD COLUMN IF NOT EXISTS kinship VARCHAR(20),
    ADD COLUMN IF NOT EXISTS mobility_level VARCHAR(20),
    ADD COLUMN IF NOT EXISTS emergency_contact_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS emergency_contact_phone VARCHAR(15),
    ADD COLUMN IF NOT EXISTS consent_given_at TIMESTAMP WITHOUT TIME ZONE;
