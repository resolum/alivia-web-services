-- Datos locales abundantes para probar la aplicacion movil de Alivia.
--
-- Crea 10 familias completas. Cada una incluye un familiar, una persona asistida,
-- un cuidador, hogar, perfil asistencial, red de cuidado, siete actividades con
-- horarios y resultados historicos. Es idempotente y usa datos reservados .test.
-- Todas las cuentas pueden iniciar sesion con: Alivia#Demo2026

BEGIN;

CREATE TEMP TABLE seed_mobile_families (
    family_no integer PRIMARY KEY,
    relative_email varchar(254) NOT NULL,
    relative_dni varchar(255) NOT NULL,
    relative_first_name varchar(255) NOT NULL,
    relative_last_name varchar(255) NOT NULL,
    caregiver_email varchar(254) NOT NULL,
    caregiver_dni varchar(255) NOT NULL,
    caregiver_first_name varchar(255) NOT NULL,
    caregiver_last_name varchar(255) NOT NULL,
    assisted_email varchar(254) NOT NULL,
    assisted_dni varchar(255) NOT NULL,
    assisted_first_name varchar(255) NOT NULL,
    assisted_last_name varchar(255) NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_mobile_families
SELECT
    n,
    format('familiar%s@alivia.test', lpad(n::text, 2, '0')),
    format('71%s', lpad(n::text, 6, '0')),
    (ARRAY['Lucia','Carlos','Mariana','Jorge','Patricia','Miguel','Rosa','Fernando','Claudia','Andres'])[n],
    (ARRAY['Salazar','Rojas','Vargas','Castro','Flores','Mendoza','Paredes','Navarro','Chavez','Herrera'])[n],
    format('cuidador%s@alivia.test', lpad(n::text, 2, '0')),
    format('72%s', lpad(n::text, 6, '0')),
    (ARRAY['Valeria','Jose','Camila','Luis','Daniela','Marco','Sofia','Ricardo','Elena','Diego'])[n],
    (ARRAY['Quispe','Torres','Ruiz','Diaz','Soto','Vega','Campos','Medina','Leon','Silva'])[n],
    format('asistido%s@alivia.test', lpad(n::text, 2, '0')),
    format('73%s', lpad(n::text, 6, '0')),
    (ARRAY['Elena','Roberto','Carmen','Alberto','Teresa','Oscar','Julia','Hector','Marta','Raul'])[n],
    (ARRAY['Salazar','Rojas','Vargas','Castro','Flores','Mendoza','Paredes','Navarro','Chavez','Herrera'])[n]
FROM generate_series(1, 10) AS n;

CREATE TEMP TABLE seed_mobile_people (
    family_no integer NOT NULL,
    person_kind varchar(16) NOT NULL,
    account_id varchar(36) NOT NULL,
    email varchar(254) NOT NULL,
    dni varchar(255) NOT NULL,
    first_name varchar(255) NOT NULL,
    last_name varchar(255) NOT NULL,
    phone_number varchar(255) NOT NULL,
    birth_date date NOT NULL,
    access_role varchar(32) NOT NULL,
    PRIMARY KEY (family_no, person_kind)
) ON COMMIT DROP;

INSERT INTO seed_mobile_people
SELECT family_no, 'RELATIVE', '10000000-0000-4000-8000-' || lpad(family_no::text, 12, '0'),
       relative_email, relative_dni, relative_first_name, relative_last_name,
       format('5191000%s', lpad(family_no::text, 4, '0')), DATE '1980-01-01' + family_no * 137,
       'CAREGIVER'
FROM seed_mobile_families
UNION ALL
SELECT family_no, 'CAREGIVER', '20000000-0000-4000-8000-' || lpad(family_no::text, 12, '0'),
       caregiver_email, caregiver_dni, caregiver_first_name, caregiver_last_name,
       format('5192000%s', lpad(family_no::text, 4, '0')), DATE '1990-01-01' + family_no * 113,
       'CAREGIVER'
FROM seed_mobile_families
UNION ALL
SELECT family_no, 'ASSISTED', '30000000-0000-4000-8000-' || lpad(family_no::text, 12, '0'),
       assisted_email, assisted_dni, assisted_first_name, assisted_last_name,
       format('5193000%s', lpad(family_no::text, 4, '0')), DATE '1940-01-01' + family_no * 211,
       'ASSISTED_PERSON'
FROM seed_mobile_families;

INSERT INTO person_profiles (
    created_at, updated_at, email, phone_number, photo_url, photo_public_id,
    date, dni, first_name, last_name, preferences
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, email, phone_number,
       format('https://api.dicebear.com/9.x/initials/svg?seed=%s%%20%s', first_name, last_name),
       format('seed-mobile-%s-%s', family_no, lower(person_kind)), birth_date, dni,
       first_name, last_name,
       CASE WHEN person_kind = 'ASSISTED'
            THEN '{"values":{"emailNotifications":false,"pushNotifications":true,"smsNotifications":false}}'::jsonb
            ELSE '{"values":{"emailNotifications":true,"pushNotifications":true,"smsNotifications":true}}'::jsonb END
FROM seed_mobile_people
ON CONFLICT (dni) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, email = EXCLUDED.email, phone_number = EXCLUDED.phone_number,
    photo_url = EXCLUDED.photo_url, photo_public_id = EXCLUDED.photo_public_id,
    date = EXCLUDED.date, first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name,
    preferences = EXCLUDED.preferences;

INSERT INTO users (
    created_at, updated_at, access_role, account_id, email,
    email_verification_expires_at, email_verification_requested_at,
    email_verification_token_hash, email_verified, failed_access_attempts,
    password_hash, profile_id, refresh_token_expires_at, refresh_token_hash,
    status, terms_accepted_at
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, p.access_role, p.account_id, p.email,
       NULL, NULL, NULL, TRUE, 0,
       '$2y$10$YZOk.Sw8.7j3Ruvm5MWSNOE/ls/sdlfV5s5aIcXGUzarkSS0bZjTG',
       pp.id, NULL, NULL, 'ACTIVE', CURRENT_TIMESTAMP
FROM seed_mobile_people p
JOIN person_profiles pp ON pp.dni = p.dni
ON CONFLICT (email) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, access_role = EXCLUDED.access_role,
    account_id = EXCLUDED.account_id, email_verified = TRUE, failed_access_attempts = 0,
    password_hash = EXCLUDED.password_hash, profile_id = EXCLUDED.profile_id, status = 'ACTIVE';

UPDATE person_profiles pp
SET user_id = u.id, updated_at = CURRENT_TIMESTAMP
FROM seed_mobile_people p
JOIN users u ON u.email = p.email
WHERE pp.dni = p.dni AND pp.user_id IS DISTINCT FROM u.id;

INSERT INTO households (
    created_at, updated_at, alias, formatted_address, latitude, longitude,
    place_id, contact_phone, contact_email
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, format('Hogar demo %s', lpad(family_no::text, 2, '0')),
       format('Av. Demo %s, Miraflores, Lima', 100 + family_no),
       -12.12 - family_no * 0.002, -77.03 - family_no * 0.002,
       format('place-demo-mobile-%s', family_no),
       format('5191000%s', lpad(family_no::text, 4, '0')), relative_email
FROM seed_mobile_families f
WHERE NOT EXISTS (
    SELECT 1 FROM households h
    WHERE h.alias = format('Hogar demo %s', lpad(f.family_no::text, 2, '0'))
);

UPDATE households h
SET updated_at = CURRENT_TIMESTAMP,
    formatted_address = format('Av. Demo %s, Miraflores, Lima', 100 + f.family_no),
    latitude = -12.12 - f.family_no * 0.002, longitude = -77.03 - f.family_no * 0.002,
    place_id = format('place-demo-mobile-%s', f.family_no),
    contact_phone = format('5191000%s', lpad(f.family_no::text, 4, '0')),
    contact_email = f.relative_email
FROM seed_mobile_families f
WHERE h.alias = format('Hogar demo %s', lpad(f.family_no::text, 2, '0'));

INSERT INTO household_members (
    created_at, updated_at, household_id, user_id, responsibility, status
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, h.id, u.id,
       CASE p.person_kind WHEN 'RELATIVE' THEN 'PRINCIPAL' WHEN 'CAREGIVER' THEN 'CAREGIVER' ELSE 'GUEST' END,
       'ACTIVE'
FROM seed_mobile_people p
JOIN users u ON u.email = p.email
JOIN households h ON h.alias = format('Hogar demo %s', lpad(p.family_no::text, 2, '0'))
ON CONFLICT (household_id, user_id) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, responsibility = EXCLUDED.responsibility, status = 'ACTIVE';

INSERT INTO assisted_person_care_profiles (
    created_at, updated_at, registered_by_user_id, household_id, first_name, last_name,
    birth_date, document_type, document_number, kinship, phone_number, mobility_level,
    emergency_contact_name, emergency_contact_phone, consent_given_at
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ru.id, h.id, f.assisted_first_name, f.assisted_last_name,
       ap.date, 'DNI', f.assisted_dni,
       (ARRAY['PARENT','GRANDPARENT','PARENT','AUNT_UNCLE','PARENT','GRANDPARENT','OTHER','PARENT','SPOUSE','SIBLING'])[f.family_no],
       ap.phone_number,
       (ARRAY['PARTIAL','VERY_LIMITED','PARTIAL','NO_LIMB_MOBILITY','VERY_LIMITED','PARTIAL','NO_LIMB_MOBILITY','PARTIAL','VERY_LIMITED','PARTIAL'])[f.family_no],
       concat(f.relative_first_name, ' ', f.relative_last_name), rp.phone_number, CURRENT_TIMESTAMP
FROM seed_mobile_families f
JOIN users ru ON ru.email = f.relative_email
JOIN person_profiles rp ON rp.dni = f.relative_dni
JOIN person_profiles ap ON ap.dni = f.assisted_dni
JOIN households h ON h.alias = format('Hogar demo %s', lpad(f.family_no::text, 2, '0'))
ON CONFLICT (registered_by_user_id) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, household_id = EXCLUDED.household_id,
    first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name,
    birth_date = EXCLUDED.birth_date, document_type = EXCLUDED.document_type,
    document_number = EXCLUDED.document_number, kinship = EXCLUDED.kinship,
    phone_number = EXCLUDED.phone_number, mobility_level = EXCLUDED.mobility_level,
    emergency_contact_name = EXCLUDED.emergency_contact_name,
    emergency_contact_phone = EXCLUDED.emergency_contact_phone,
    consent_given_at = EXCLUDED.consent_given_at;

-- Cada persona asistida queda vinculada a su familiar y a su cuidador profesional.
INSERT INTO care_memberships (
    created_at, updated_at, assisted_person_profile_id, member_person_profile_id,
    role, status, user_id
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, assisted.id, member.id,
       CASE p.person_kind WHEN 'RELATIVE' THEN 'RELATIVE' ELSE 'CAREGIVER' END,
       'ACTIVE', u.id
FROM seed_mobile_people p
JOIN seed_mobile_families f ON f.family_no = p.family_no
JOIN person_profiles assisted ON assisted.dni = f.assisted_dni
JOIN person_profiles member ON member.dni = p.dni
JOIN users u ON u.email = p.email
WHERE p.person_kind IN ('RELATIVE', 'CAREGIVER')
ON CONFLICT (user_id, assisted_person_profile_id, role) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, member_person_profile_id = EXCLUDED.member_person_profile_id,
    status = 'ACTIVE';

CREATE TEMP TABLE seed_mobile_activity_templates (
    activity_no integer PRIMARY KEY,
    type varchar(20) NOT NULL,
    title varchar(120) NOT NULL,
    description varchar(500) NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL,
    week_days varchar(80) NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_mobile_activity_templates VALUES
    (1, 'MEDICATION', 'Losartan 50 mg', 'Tomar una tableta con agua despues del desayuno. [SEED-MOBILE]', TIME '08:00', TIME '08:30', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'),
    (2, 'MEDICATION', 'Metformina 850 mg', 'Tomar con la cena; verificar tolerancia. [SEED-MOBILE]', TIME '19:00', TIME '19:30', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'),
    (3, 'MEAL', 'Desayuno nutritivo', 'Preparar desayuno bajo en sodio. [SEED-MOBILE]', TIME '08:30', TIME '09:15', 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY'),
    (4, 'EXERCISE', 'Caminata asistida', 'Caminata suave y ejercicios de equilibrio. [SEED-MOBILE]', TIME '10:30', TIME '11:15', 'MONDAY,WEDNESDAY,FRIDAY'),
    (5, 'THERAPY', 'Ejercicios de memoria', 'Lectura, orientacion y juego cognitivo. [SEED-MOBILE]', TIME '16:00', TIME '16:45', 'TUESDAY,THURSDAY,SATURDAY'),
    (6, 'BATH', 'Aseo personal', 'Apoyar respetando privacidad y medidas de seguridad. [SEED-MOBILE]', TIME '07:00', TIME '07:45', 'MONDAY,WEDNESDAY,FRIDAY,SUNDAY'),
    (7, 'APPOINTMENT', 'Control de salud', 'Revisar signos vitales y registrar observaciones. [SEED-MOBILE]', TIME '15:00', TIME '16:00', 'FRIDAY');

INSERT INTO care_activities (
    created_at, updated_at, assisted_person_profile_id, description, status, title, type
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ap.id, t.description, 'ACTIVE', t.title, t.type
FROM seed_mobile_families f
JOIN person_profiles ap ON ap.dni = f.assisted_dni
CROSS JOIN seed_mobile_activity_templates t
WHERE NOT EXISTS (
    SELECT 1 FROM care_activities ca
    WHERE ca.assisted_person_profile_id = ap.id AND ca.title = t.title
      AND ca.description LIKE '%[SEED-MOBILE]%'
);

UPDATE care_activities ca
SET updated_at = CURRENT_TIMESTAMP, type = t.type, description = t.description, status = 'ACTIVE'
FROM seed_mobile_activity_templates t
WHERE ca.title = t.title AND ca.description LIKE '%[SEED-MOBILE]%';

INSERT INTO activity_assignments (
    created_at, updated_at, caregiver_id, end_time, schedule_type, scheduled_date,
    start_time, status, week_days, care_activity_id
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       CASE WHEN t.activity_no IN (1, 2, 4, 6) THEN cm.id ELSE NULL END,
       t.end_time, 'WEEKLY', NULL, t.start_time, 'ACTIVE', t.week_days, ca.id
FROM seed_mobile_families f
JOIN person_profiles ap ON ap.dni = f.assisted_dni
JOIN care_activities ca ON ca.assisted_person_profile_id = ap.id AND ca.description LIKE '%[SEED-MOBILE]%'
JOIN seed_mobile_activity_templates t ON t.title = ca.title
LEFT JOIN users cu ON cu.email = f.caregiver_email
LEFT JOIN care_memberships cm ON cm.user_id = cu.id
    AND cm.assisted_person_profile_id = ap.id AND cm.role = 'CAREGIVER'
WHERE NOT EXISTS (
    SELECT 1 FROM activity_assignments aa
    WHERE aa.care_activity_id = ca.id AND aa.schedule_type = 'WEEKLY'
      AND aa.start_time = t.start_time AND aa.end_time = t.end_time
);

-- Dos semanas de resultados permiten probar estados completado/omitido y puntual/tardio.
INSERT INTO activity_logs (
    created_at, updated_at, assigned_caregiver_id, assignment_id,
    assisted_person_profile_id, care_activity_id, completed_at, completed_by,
    occurrence_date, end_time, late, notes, omission_reason, registered_at,
    start_time, status, title
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, aa.caregiver_id, aa.id,
       ca.assisted_person_profile_id, ca.id,
       d.day + aa.start_time + CASE WHEN (ca.id + extract(day FROM d.day)::bigint) % 5 = 0
                                   THEN interval '50 minutes' ELSE interval '10 minutes' END,
       aa.caregiver_id, d.day, aa.end_time,
       ((ca.id + extract(day FROM d.day)::bigint) % 5 = 0),
       CASE WHEN (ca.id + extract(day FROM d.day)::bigint) % 7 = 0
            THEN 'Se registro una observacion de demostracion.' ELSE NULL END,
       CASE WHEN (ca.id + extract(day FROM d.day)::bigint) % 9 = 0
            THEN 'La persona asistida indico malestar.' ELSE NULL END,
       d.day + aa.end_time + interval '5 minutes', aa.start_time,
       CASE WHEN (ca.id + extract(day FROM d.day)::bigint) % 9 = 0 THEN 'OMITTED' ELSE 'COMPLETED' END,
       ca.title
FROM activity_assignments aa
JOIN care_activities ca ON ca.id = aa.care_activity_id AND ca.description LIKE '%[SEED-MOBILE]%'
CROSS JOIN LATERAL (
    SELECT day::date
    FROM generate_series(CURRENT_DATE - 14, CURRENT_DATE - 1, interval '1 day') AS day
) d
WHERE aa.schedule_type = 'WEEKLY'
  AND position(upper(to_char(d.day, 'FMDay')) IN aa.week_days) > 0
ON CONFLICT (assignment_id, occurrence_date) DO NOTHING;

COMMIT;

SELECT
    (SELECT count(*) FROM person_profiles WHERE email LIKE '%@alivia.test') AS demo_profiles,
    (SELECT count(*) FROM households WHERE alias LIKE 'Hogar demo %') AS demo_households,
    (SELECT count(*) FROM care_memberships cm JOIN users u ON u.id = cm.user_id
        WHERE u.email LIKE '%@alivia.test') AS care_links,
    (SELECT count(*) FROM care_activities WHERE description LIKE '%[SEED-MOBILE]%') AS activities,
    (SELECT count(*) FROM activity_logs al JOIN care_activities ca ON ca.id = al.care_activity_id
        WHERE ca.description LIKE '%[SEED-MOBILE]%') AS historical_results;
