-- Fundaciones compartidas por los bounded contexts: empresa, administrador y catalogo.
-- Idempotente y reservado exclusivamente para desarrollo local.

BEGIN;

INSERT INTO staff_profiles (
    created_at, updated_at, email, phone_number, date, dni, first_name, last_name, preferences
) VALUES (
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'admin@alivia.test', '51900000999', DATE '1988-05-15',
    '70999999', 'Andrea', 'Administrador',
    '{"values":{"emailNotifications":true,"pushNotifications":true,"smsNotifications":false}}'::jsonb
)
ON CONFLICT (dni) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, email = EXCLUDED.email, phone_number = EXCLUDED.phone_number,
    first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, preferences = EXCLUDED.preferences;

INSERT INTO users (
    created_at, updated_at, access_role, account_id, email, email_verified,
    failed_access_attempts, password_hash, profile_id, status, terms_accepted_at
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ADMINISTRATOR',
       '00000000-0000-4000-8000-000000000001', 'admin@alivia.test', TRUE, 0,
       '$2y$10$YZOk.Sw8.7j3Ruvm5MWSNOE/ls/sdlfV5s5aIcXGUzarkSS0bZjTG', sp.id, 'ACTIVE', CURRENT_TIMESTAMP
FROM staff_profiles sp WHERE sp.dni = '70999999'
ON CONFLICT (email) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, access_role = 'ADMINISTRATOR', email_verified = TRUE,
    password_hash = EXCLUDED.password_hash, profile_id = EXCLUDED.profile_id, status = 'ACTIVE';

UPDATE staff_profiles sp
SET user_id = u.id, updated_at = CURRENT_TIMESTAMP
FROM users u
WHERE sp.dni = '70999999' AND u.email = 'admin@alivia.test';

INSERT INTO administrators (created_at, updated_at, user_id, staff_profile_id, backup_personal_email)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, u.id, sp.id, 'admin.backup@alivia.test'
FROM users u JOIN staff_profiles sp ON sp.dni = '70999999'
WHERE u.email = 'admin@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM administrators a WHERE a.user_id = u.id);

INSERT INTO business_profiles (
    created_at, updated_at, email, phone_number, formatted_address, latitude, longitude,
    place_id, business_hours, business_name, business_phone, description, name,
    main_activity, ruc, slogan, support_email, preferences
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'contacto@alivia.test', '51980000000',
       'Av. Javier Prado 1234, San Isidro, Lima', -12.095, -77.036, 'place-alivia-demo',
       '{"MONDAY":"08:00-18:00","TUESDAY":"08:00-18:00","WEDNESDAY":"08:00-18:00","THURSDAY":"08:00-18:00","FRIDAY":"08:00-18:00"}'::jsonb,
       'Alivia Demo', '51980000000', 'Empresa local de demostracion para cuidado asistido.',
       'Alivia Servicios Integrales S.A.C.', 'Tecnologia y cuidado asistido', '20999999991',
       'Cuidamos contigo', 'soporte@alivia.test',
       '{"values":{"emailNotifications":true,"pushNotifications":true,"smsNotifications":true}}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM business_profiles WHERE ruc = '20999999991');

INSERT INTO businesses (created_at, updated_at, business_profile_id, administrator_id)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, bp.id, a.id
FROM business_profiles bp CROSS JOIN administrators a
WHERE bp.ruc = '20999999991'
  AND a.user_id = (SELECT id FROM users WHERE email = 'admin@alivia.test')
  AND NOT EXISTS (SELECT 1 FROM businesses b WHERE b.business_profile_id = bp.id);

INSERT INTO plans (
    additional_room_monthly_amount, base_price_amount, code, currency, description,
    included_actuators, max_actuators, min_actuators, minimum_rooms, name,
    price_qualifier, status, stripe_actuator_price_id, stripe_base_price_id
) VALUES
    (NULL, 99.00, 'MINIMUM', 'PEN',
     'Asistencia inicial en una habitacion con un actuador a eleccion.',
     1, 1, 1, 1, 'Plan Base', 'FIXED', 'ACTIVE', NULL, 'price_demo_minimum'),
    (35.00, 149.00, 'CUSTOM', 'PEN',
     'Configura varias habitaciones y de uno a cuatro actuadores.',
     0, 4, 1, 2, 'Plan Personalizado', 'BASE_COMPONENT', 'ACTIVE',
     'price_demo_actuator', 'price_demo_custom')
ON CONFLICT (code) DO UPDATE SET
    base_price_amount = EXCLUDED.base_price_amount, description = EXCLUDED.description,
    status = 'ACTIVE', stripe_actuator_price_id = EXCLUDED.stripe_actuator_price_id,
    stripe_base_price_id = EXCLUDED.stripe_base_price_id;

DELETE FROM plan_features WHERE plan_id IN (SELECT id FROM plans WHERE code IN ('MINIMUM', 'CUSTOM'));
DELETE FROM plan_included_devices WHERE plan_id IN (SELECT id FROM plans WHERE code IN ('MINIMUM', 'CUSTOM'));
DELETE FROM plan_device_monthly_prices WHERE plan_id IN (SELECT id FROM plans WHERE code = 'CUSTOM');

INSERT INTO plan_features (plan_id, feature, display_order)
SELECT p.id, f.feature, f.display_order
FROM plans p
JOIN (VALUES
    ('MINIMUM', 'Una habitacion y un actuador a eleccion', 0),
    ('MINIMUM', 'Control por voz', 1),
    ('MINIMUM', 'App Alivia para cuidadores y familiares', 2),
    ('MINIMUM', 'Evaluacion tecnica sin costo', 3),
    ('CUSTOM', 'Dos habitaciones incluidas', 0),
    ('CUSTOM', 'Uno a cuatro actuadores a medida', 1),
    ('CUSTOM', 'Nombres por habitacion para control por voz', 2),
    ('CUSTOM', 'App con multiples cuidadores y alertas zonales', 3),
    ('CUSTOM', 'Evaluacion tecnica sin recargo', 4)
) AS f(code, feature, display_order) ON f.code = p.code;

INSERT INTO plan_included_devices (plan_id, device, display_order)
SELECT p.id, d.device, d.display_order
FROM plans p
JOIN (VALUES
    ('MINIMUM', 'Microfono', 0), ('MINIMUM', 'Un actuador a eleccion', 1),
    ('CUSTOM', 'Microfono', 0)
) AS d(code, device, display_order) ON d.code = p.code;

INSERT INTO plan_device_monthly_prices (plan_id, device_type, monthly_price_amount)
SELECT p.id, d.device_type, d.amount
FROM plans p
CROSS JOIN (VALUES ('LIGHT', 15.00), ('DOOR', 25.00), ('WINDOW', 25.00), ('CURTAIN', 20.00))
    AS d(device_type, amount)
WHERE p.code = 'CUSTOM';

COMMIT;
