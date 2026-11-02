-- V2: foreign keys and search indexes missing from V1 (AP-482 / P-482.1).
--
-- V1 contains exactly what Hibernate declares for the entities: primary keys, the
-- unique constraints annotated on the entities, the CHECK constraints of the enums
-- and the few indexes declared with @Index. Hibernate does not create foreign keys
-- for plain Long columns and only creates the indexes explicitly annotated, so the
-- referential integrity and the indexes of the real queries were missing.
--
-- This migration only adds objects. It does not create, drop or alter any table or
-- column, so it is safe to apply on a database that already holds data. If an
-- existing row violates one of the new constraints the migration fails instead of
-- leaving the orphan in place: that is intentional, and the row must be fixed before
-- deploying.
--
-- Hibernate validates tables, columns and types (ddl-auto=validate), not foreign
-- keys or indexes, so these objects survive the production schema check.
--
-- Rules for this file and for every later migration:
--   * Once applied anywhere, a migration is immutable: Flyway stores a checksum and
--     a mismatch aborts the startup. Change the schema with a new V<n>__*.sql file.
--   * Name constraints and indexes explicitly (fk_..., idx_...); Hibernate's
--     generated names (fk7eb8ax6h6py9uk57v19qgnjvc) are hard to trace.
--   * Do not duplicate an index already provided by a primary key or a unique
--     constraint (see docs/data-integrity.md).

-- =============================================================================
-- Foreign keys
-- =============================================================================

-- Nursing: the membership links an IAM user and two profiles, and the assignments
-- and logs refer to the caregiver membership (not to users.id).

ALTER TABLE care_memberships
    ADD CONSTRAINT fk_care_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE care_memberships
    ADD CONSTRAINT fk_care_memberships_member_profile
        FOREIGN KEY (member_person_profile_id) REFERENCES person_profiles (id);

ALTER TABLE care_memberships
    ADD CONSTRAINT fk_care_memberships_assisted_person
        FOREIGN KEY (assisted_person_profile_id) REFERENCES person_profiles (id);

ALTER TABLE care_activities
    ADD CONSTRAINT fk_care_activities_assisted_person
        FOREIGN KEY (assisted_person_profile_id) REFERENCES person_profiles (id);

-- A null caregiver means the relative does the activity, so the column stays nullable.
ALTER TABLE activity_assignments
    ADD CONSTRAINT fk_activity_assignments_caregiver
        FOREIGN KEY (caregiver_id) REFERENCES care_memberships (id);

ALTER TABLE activity_logs
    ADD CONSTRAINT fk_activity_logs_assignment
        FOREIGN KEY (assignment_id) REFERENCES activity_assignments (id);

ALTER TABLE activity_logs
    ADD CONSTRAINT fk_activity_logs_care_activity
        FOREIGN KEY (care_activity_id) REFERENCES care_activities (id);

ALTER TABLE activity_logs
    ADD CONSTRAINT fk_activity_logs_assisted_person
        FOREIGN KEY (assisted_person_profile_id) REFERENCES person_profiles (id);

-- Copy of the assignment caregiver in force that day; null when the relative did it.
ALTER TABLE activity_logs
    ADD CONSTRAINT fk_activity_logs_assigned_caregiver
        FOREIGN KEY (assigned_caregiver_id) REFERENCES care_memberships (id);

-- The caregiver membership that recorded the outcome; null when a relative recorded it.
ALTER TABLE activity_logs
    ADD CONSTRAINT fk_activity_logs_completed_by
        FOREIGN KEY (completed_by) REFERENCES care_memberships (id);

-- HCM: an employee always has an IAM user and a staff profile, and a contract,
-- a work schedule and a change log always belong to an existing employee/contract.

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_user
        FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE employees
    ADD CONSTRAINT fk_employees_staff_profile
        FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id);

ALTER TABLE contracts
    ADD CONSTRAINT fk_contracts_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id);

ALTER TABLE contract_versions
    ADD CONSTRAINT fk_contract_versions_contract
        FOREIGN KEY (contract_id) REFERENCES contracts (id);

ALTER TABLE contract_versions
    ADD CONSTRAINT fk_contract_versions_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id);

ALTER TABLE work_schedules
    ADD CONSTRAINT fk_work_schedules_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id);

ALTER TABLE work_schedules
    ADD CONSTRAINT fk_work_schedules_contract
        FOREIGN KEY (contract_id) REFERENCES contracts (id);

ALTER TABLE work_schedules
    ADD CONSTRAINT fk_work_schedules_last_modified_by
        FOREIGN KEY (last_modified_by) REFERENCES users (id);

ALTER TABLE employee_change_logs
    ADD CONSTRAINT fk_employee_change_logs_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id);

-- =============================================================================
-- Search indexes
-- =============================================================================

-- Registration and profile creation check these columns with existsBy* queries.
CREATE INDEX idx_person_profiles_email ON person_profiles USING btree (email);
CREATE INDEX idx_staff_profiles_email ON staff_profiles USING btree (email);
CREATE INDEX idx_business_profiles_email ON business_profiles USING btree (email);
CREATE INDEX idx_business_profiles_ruc ON business_profiles USING btree (ruc);
CREATE INDEX idx_business_profiles_business_name ON business_profiles USING btree (business_name);
-- LegalName is embedded in the "name" column (V1), not in "legal_name".
CREATE INDEX idx_business_profiles_legal_name ON business_profiles USING btree (name);

-- Active memberships of an assisted person, used to resolve the caregiver role.
-- The unique (user_id, assisted_person_profile_id, role) already covers lookups by
-- user_id, so no separate (user_id, status) index is added.
CREATE INDEX idx_care_memberships_assisted_person_status
    ON care_memberships USING btree (assisted_person_profile_id, status);

-- Care agenda: activities of a set of assisted persons.
CREATE INDEX idx_care_activities_assisted_person
    ON care_activities USING btree (assisted_person_profile_id);

-- Assignments are fetched through their care activity (join fetch and assignment lookup).
CREATE INDEX idx_activity_assignments_care_activity
    ON activity_assignments USING btree (care_activity_id);

-- Employee listing is always filtered by business and paginated by id.
CREATE INDEX idx_employees_business ON employees USING btree (business_id);

-- PaymentRepository.findFirstBySubscription_IdAndStripeInvoiceIdIsNullOrderByIdDesc.
CREATE INDEX idx_payments_subscription ON payments USING btree (subscription_id);
