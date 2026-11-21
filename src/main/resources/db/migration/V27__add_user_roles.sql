-- V27: a user can hold several roles (US-17).
--
-- users.access_role held one role per account. The roles move to user_roles, one row per role,
-- and the column is dropped. CLEANING and RELATIVE are new roles.
-- Cleaning employees were invited as TECHNICIAN because IAM had no CLEANING role: their row is
-- corrected from the employee's HCM role.

CREATE TABLE user_roles (
    user_id bigint NOT NULL,
    role character varying(32) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_roles_role_check CHECK (role IN ('ADMINISTRATOR', 'CAREGIVER', 'TECHNICIAN',
        'SUBSCRIPTION_MANAGER', 'ASSISTED_PERSON', 'CLEANING', 'RELATIVE'))
);

INSERT INTO user_roles (user_id, role)
SELECT id, access_role FROM users;

UPDATE user_roles
SET role = 'CLEANING'
FROM employees
WHERE employees.user_id = user_roles.user_id
  AND employees.role = 'CLEANING'
  AND user_roles.role = 'TECHNICIAN';

ALTER TABLE users DROP COLUMN access_role;
