-- V13: schema parity after merging develop (AP-46) and optimistic locking of assets (AP-68).
--
-- 1. support_incidents: develop's Incident entity (AP-46) gained five columns and the IN_EVALUATION status without a
--    migration. V9 created the table before that, so a database built only by Flyway failed the schema validation of the
--    production profile (ddl-auto=validate) with "missing column [device_alias] in table [support_incidents]".
-- 2. The enum check constraints that the merged enums outgrew: the status of appointments (COMPLETED), the action of
--    contract versions (ACTIVATED) and the status of incidents and of their history (IN_EVALUATION). With ddl-auto=update
--    EnumCheckConstraintSynchronizer rewrites them at startup; with Flyway alone they stayed stale.
-- 3. devices and edges get the version column of Asset (@Version): two requests that change the same asset no longer
--    overwrite each other silently; the later one fails and is reported as a conflict.
--
-- V9 is already published, so it is not edited. Every statement can be repeated.

-- 1. incident columns (all optional: incidents created before AP-46 do not have them)
ALTER TABLE support_incidents
    ADD COLUMN IF NOT EXISTS occurred_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS device_alias character varying(60),
    ADD COLUMN IF NOT EXISTS device_mac character varying(17),
    ADD COLUMN IF NOT EXISTS device_type character varying(16),
    ADD COLUMN IF NOT EXISTS problem_type character varying(20);

ALTER TABLE support_incidents
    DROP CONSTRAINT IF EXISTS support_incidents_problem_type_check;
ALTER TABLE support_incidents
    ADD CONSTRAINT support_incidents_problem_type_check
        CHECK (problem_type IN ('BATTERY', 'NO_RESPONSE', 'FALSE_ALARM', 'CONNECTIVITY', 'PHYSICAL_DAMAGE', 'OTHER'));

-- 2. enum checks
ALTER TABLE support_incidents
    DROP CONSTRAINT IF EXISTS support_incidents_status_check;
ALTER TABLE support_incidents
    ADD CONSTRAINT support_incidents_status_check
        CHECK (status IN ('OPEN', 'IN_EVALUATION', 'SCHEDULED', 'IN_PROGRESS', 'RESOLVED'));

ALTER TABLE support_incident_status_histories
    DROP CONSTRAINT IF EXISTS support_incident_status_histories_status_check;
ALTER TABLE support_incident_status_histories
    ADD CONSTRAINT support_incident_status_histories_status_check
        CHECK (status IN ('OPEN', 'IN_EVALUATION', 'SCHEDULED', 'IN_PROGRESS', 'RESOLVED'));

ALTER TABLE appointments
    DROP CONSTRAINT IF EXISTS appointments_status_check;
ALTER TABLE appointments
    ADD CONSTRAINT appointments_status_check
        CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELED'));

ALTER TABLE contract_versions
    DROP CONSTRAINT IF EXISTS contract_versions_action_check;
ALTER TABLE contract_versions
    ADD CONSTRAINT contract_versions_action_check
        CHECK (action IN ('CREATED', 'UPDATED', 'ACTIVATED', 'RENEWED', 'TERMINATED'));

-- 3. optimistic locking of assets
ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE edges
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
