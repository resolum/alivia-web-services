-- Datos conectados para subscriptions, operations, assets, edge transport y support.
-- Depende de seed-foundation-demo.sql, seed-hcm-demo.sql, seed-mobile-demo.sql y seed-hiring-demo.sql.

BEGIN;

-- Assistance: familiares, personas asistidas y cuidadores construidos desde los perfiles moviles.
INSERT INTO relatives (created_at, updated_at, user_id, person_profile_id, business_id)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, u.id, pp.id, b.id
FROM users u
JOIN person_profiles pp ON pp.user_id = u.id
CROSS JOIN businesses b
WHERE u.email LIKE 'familiar%@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM relatives r WHERE r.user_id = u.id);

INSERT INTO assisted_persons (
    created_at, updated_at, person_profile_id, relative_id, household_id, kinship,
    mobility_level, emergency_contact_name, emergency_contact_phone, consent_given_at
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, assisted.id, r.id, h.id,
       'PARENT', 'PARTIAL', concat(relative_profile.first_name, ' ', relative_profile.last_name),
       relative_profile.phone_number, CURRENT_TIMESTAMP
FROM relatives r
JOIN users relative_user ON relative_user.id = r.user_id
JOIN person_profiles relative_profile ON relative_profile.id = r.person_profile_id
JOIN person_profiles assisted
    ON assisted.email = replace(relative_user.email, 'familiar', 'asistido')
JOIN households h ON h.contact_email = relative_user.email
WHERE NOT EXISTS (SELECT 1 FROM assisted_persons ap WHERE ap.person_profile_id = assisted.id);

INSERT INTO caregivers (created_at, updated_at, user_id, assisted_person_id, status)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, caregiver_user.id, ap.id, 'ACTIVE'
FROM assisted_persons ap
JOIN person_profiles assisted_profile ON assisted_profile.id = ap.person_profile_id
JOIN users caregiver_user
    ON caregiver_user.email = replace(assisted_profile.email, 'asistido', 'cuidador')
WHERE NOT EXISTS (
    SELECT 1 FROM caregivers c
    WHERE c.user_id = caregiver_user.id AND c.assisted_person_id = ap.id
);

-- El familiar principal tambien puede cubrir un turno de emergencia en la familia 01.
INSERT INTO caregivers (created_at, updated_at, user_id, assisted_person_id, status)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, r.user_id, ap.id, 'ACTIVE'
FROM assisted_persons ap JOIN relatives r ON r.id = ap.relative_id
JOIN person_profiles pp ON pp.id = ap.person_profile_id
WHERE pp.email = 'asistido01@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM caregivers c WHERE c.user_id = r.user_id AND c.assisted_person_id = ap.id);

INSERT INTO assistance_invitations (
    created_at, updated_at, assisted_person_id, inviter_id, invitee_email,
    caregiver_user_id, status, expires_at
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ap.id, r.id, 'cuidador02@alivia.test',
       u.id, 'PENDING', CURRENT_TIMESTAMP + interval '7 days'
FROM assisted_persons ap JOIN relatives r ON r.id = ap.relative_id
JOIN person_profiles pp ON pp.id = ap.person_profile_id
JOIN users u ON u.email = 'cuidador02@alivia.test'
WHERE pp.email = 'asistido01@alivia.test'
  AND NOT EXISTS (
      SELECT 1 FROM assistance_invitations i
      WHERE i.assisted_person_id = ap.id AND i.invitee_email = 'cuidador02@alivia.test' AND i.status = 'PENDING'
  );

INSERT INTO caregiver_invitations (
    created_at, updated_at, token, household_id, assisted_person_profile_id,
    email, inviter_user_id, relationship, status, expires_at
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
       'seed-demo-caregiver-invitation-0000000000000000000000000000001',
       h.id, assisted.id, 'nuevo.cuidador@alivia.test', relative_user.id,
       'PROFESSIONAL_CAREGIVER', 'ACTIVE', CURRENT_TIMESTAMP + interval '7 days'
FROM households h
JOIN person_profiles assisted ON assisted.email = 'asistido01@alivia.test'
JOIN users relative_user ON relative_user.email = 'familiar01@alivia.test'
WHERE h.alias = 'Hogar demo 01'
ON CONFLICT (token) DO UPDATE SET expires_at = EXCLUDED.expires_at, status = 'ACTIVE';

DELETE FROM caregiver_invitation_responsibilities
WHERE invitation_id = (SELECT id FROM caregiver_invitations WHERE token = 'seed-demo-caregiver-invitation-0000000000000000000000000000001');
INSERT INTO caregiver_invitation_responsibilities (invitation_id, responsibility)
SELECT id, responsibility
FROM caregiver_invitations
CROSS JOIN (VALUES ('MEDICATION'), ('MOBILITY'), ('MEALS')) AS x(responsibility)
WHERE token = 'seed-demo-caregiver-invitation-0000000000000000000000000000001';

-- Dos turnos consecutivos y su relevo aceptado permiten probar la funcionalidad de la captura.
INSERT INTO care_shifts (
    assisted_person_id, caregiver_id, caregiver_name, starts_at, ends_at, notes,
    status, created_at, updated_at
)
SELECT ap.id, c.id, 'Valeria Quispe', CURRENT_DATE + TIME '08:00', CURRENT_DATE + TIME '14:00',
       'Turno de manana: medicacion y ejercicios.', 'COMPLETED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM assisted_persons ap
JOIN person_profiles assisted ON assisted.id = ap.person_profile_id
JOIN caregivers c ON c.assisted_person_id = ap.id
JOIN users u ON u.id = c.user_id AND u.email = 'cuidador01@alivia.test'
WHERE assisted.email = 'asistido01@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM care_shifts s WHERE s.assisted_person_id = ap.id AND s.starts_at = CURRENT_DATE + TIME '08:00');

INSERT INTO care_shifts (
    assisted_person_id, caregiver_id, caregiver_name, starts_at, ends_at, notes,
    status, created_at, updated_at
)
SELECT ap.id, c.id, 'Lucia Salazar', CURRENT_DATE + TIME '14:00', CURRENT_DATE + TIME '20:00',
       'Turno de tarde: terapia y cena.', 'SCHEDULED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM assisted_persons ap
JOIN person_profiles assisted ON assisted.id = ap.person_profile_id
JOIN caregivers c ON c.assisted_person_id = ap.id
JOIN users u ON u.id = c.user_id AND u.email = 'familiar01@alivia.test'
WHERE assisted.email = 'asistido01@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM care_shifts s WHERE s.assisted_person_id = ap.id AND s.starts_at = CURRENT_DATE + TIME '14:00');

INSERT INTO care_handovers (
    from_shift_id, to_shift_id, from_caregiver_id, from_caregiver_name,
    to_caregiver_id, to_caregiver_name, status, requested_at, responded_at,
    attended_tasks, pending_tasks, substitute, created_at, updated_at
)
SELECT from_shift.id, to_shift.id, from_shift.caregiver_id, from_shift.caregiver_name,
       to_shift.caregiver_id, to_shift.caregiver_name, 'ACCEPTED',
       CURRENT_TIMESTAMP - interval '30 minutes', CURRENT_TIMESTAMP - interval '20 minutes',
       'Medicacion de la manana administrada; ejercicios completados.',
       'Realizar terapia cognitiva y registrar la cena.', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM care_shifts from_shift
JOIN care_shifts to_shift ON to_shift.assisted_person_id = from_shift.assisted_person_id
    AND to_shift.starts_at = from_shift.ends_at
WHERE from_shift.starts_at = CURRENT_DATE + TIME '08:00'
  AND NOT EXISTS (SELECT 1 FROM care_handovers h WHERE h.from_shift_id = from_shift.id AND h.to_shift_id = to_shift.id);

-- Una contratacion cobrada perteneciente a la primera familia demo.
INSERT INTO configuration_drafts (
    id, plan_code, billing_cycle, payment_status, updated_at, preauthorized_at, capture_before,
    stripe_customer_id, stripe_setup_intent_id, stripe_payment_intent_id, confirmed_at, owner_user_id
)
SELECT '00000000-0000-4000-9000-000000000301', 'MINIMUM', 'MONTHLY', 'SUCCEEDED',
       CURRENT_TIMESTAMP - interval '15 days', CURRENT_TIMESTAMP - interval '17 days',
       CURRENT_TIMESTAMP - interval '10 days', 'cus_demo301', 'seti_demo301', 'pi_demo301',
       CURRENT_TIMESTAMP - interval '16 days', u.id
FROM users u WHERE u.email = 'familiar01@alivia.test'
ON CONFLICT (id) DO UPDATE SET
    payment_status = 'SUCCEEDED', updated_at = EXCLUDED.updated_at,
    confirmed_at = EXCLUDED.confirmed_at, owner_user_id = EXCLUDED.owner_user_id;

DELETE FROM configuration_draft_rooms WHERE draft_id = '00000000-0000-4000-9000-000000000301';
INSERT INTO configuration_draft_rooms (
    draft_id, display_order, room_key, room_name, microphone_quantity,
    light_quantity, window_quantity, door_quantity, curtain_quantity
) VALUES ('00000000-0000-4000-9000-000000000301', 0, 'main-bedroom',
          'Dormitorio principal', 1, 1, 0, 0, 0);

INSERT INTO subscriptions (
    active_household_id, assisted_person_id, business_id, currency, current_period_end,
    current_period_start, household_id, idempotency_key, monthly_amount, refunded, status,
    stripe_customer_id, stripe_subscription_id, user_id, plan_id, draft_id
)
SELECT h.id::text, ap.id, b.id, 'PEN', CURRENT_TIMESTAMP + interval '15 days',
       CURRENT_TIMESTAMP - interval '15 days', h.id::text, 'seed-demo-subscription-301',
       99.00, FALSE, 'ACTIVE', 'cus_demo301', 'sub_demo301', u.id, p.id,
       '00000000-0000-4000-9000-000000000301'
FROM users u
JOIN households h ON h.alias = 'Hogar demo 01'
JOIN person_profiles ap ON ap.email = 'asistido01@alivia.test'
CROSS JOIN plans p
CROSS JOIN businesses b
WHERE u.email = 'familiar01@alivia.test' AND p.code = 'MINIMUM'
ON CONFLICT (idempotency_key) DO UPDATE SET
    status = 'ACTIVE', active_household_id = EXCLUDED.active_household_id,
    current_period_end = EXCLUDED.current_period_end, refunded = FALSE;

DELETE FROM subscription_actuators
WHERE subscription_id = (SELECT id FROM subscriptions WHERE idempotency_key = 'seed-demo-subscription-301');
INSERT INTO subscription_actuators (subscription_id, actuator_type, quantity)
SELECT id, 'LIGHT', 1 FROM subscriptions WHERE idempotency_key = 'seed-demo-subscription-301';

INSERT INTO payments (
    amount, created_at, currency, period_start, period_end, receipt_url, status,
    stripe_invoice_id, stripe_payment_intent_id, subscription_id
)
SELECT 99.00, CURRENT_TIMESTAMP - interval '15 days', 'PEN',
       CURRENT_TIMESTAMP - interval '15 days', CURRENT_TIMESTAMP + interval '15 days',
       'https://example.test/receipts/demo-301', 'SUCCEEDED', 'in_demo301', 'pi_invoice_demo301', s.id
FROM subscriptions s WHERE s.idempotency_key = 'seed-demo-subscription-301'
  AND NOT EXISTS (SELECT 1 FROM payments WHERE stripe_payment_intent_id = 'pi_invoice_demo301');

-- Evaluacion e instalacion completadas por Ana Torres.
INSERT INTO appointments (
    created_at, updated_at, draft_id, service_type, scheduled_date, start_time, end_time,
    technician_id, technician_name, contact_name, contact_phone, directions, status,
    started_at, completed_at, completion_notes, evaluation_result
)
SELECT CURRENT_TIMESTAMP - interval '14 days', CURRENT_TIMESTAMP - interval '14 days',
       '00000000-0000-4000-9000-000000000301', 'EVALUATION', CURRENT_DATE - 14,
       TIME '09:00', TIME '11:00', e.id, 'Ana Torres', 'Lucia Salazar', '51910000001',
       'Av. Demo 101, Miraflores, Lima', 'COMPLETED',
       CURRENT_TIMESTAMP - interval '14 days', CURRENT_TIMESTAMP - interval '14 days' + interval '90 minutes',
       'Vivienda apta para la instalacion.', 'VIABLE'
FROM employees e JOIN users u ON u.id = e.user_id
WHERE u.email = 'ana.torres@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM appointments WHERE draft_id = '00000000-0000-4000-9000-000000000301' AND service_type = 'EVALUATION');

INSERT INTO appointments (
    created_at, updated_at, draft_id, service_type, scheduled_date, start_time, end_time,
    technician_id, technician_name, contact_name, contact_phone, directions, status,
    started_at, completed_at, completion_notes, installation_result, test_result
)
SELECT CURRENT_TIMESTAMP - interval '10 days', CURRENT_TIMESTAMP - interval '10 days',
       '00000000-0000-4000-9000-000000000301', 'INSTALLATION', CURRENT_DATE - 10,
       TIME '09:00', TIME '12:00', e.id, 'Ana Torres', 'Lucia Salazar', '51910000001',
       'Av. Demo 101, Miraflores, Lima', 'COMPLETED',
       CURRENT_TIMESTAMP - interval '10 days', CURRENT_TIMESTAMP - interval '10 days' + interval '150 minutes',
       'Microfono, edge y luz instalados y probados.', 'APPROVED', 'PASSED'
FROM employees e JOIN users u ON u.id = e.user_id
WHERE u.email = 'ana.torres@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM appointments WHERE draft_id = '00000000-0000-4000-9000-000000000301' AND service_type = 'INSTALLATION');

INSERT INTO appointment_assignments (
    appointment_id, assignment_order, technician_id, technician_name, scheduled_date,
    start_time, end_time, reason, assigned_at
)
SELECT a.id, 0, a.technician_id, a.technician_name, a.scheduled_date, a.start_time,
       a.end_time, 'BOOKED', a.created_at
FROM appointments a
WHERE a.draft_id = '00000000-0000-4000-9000-000000000301'
ON CONFLICT (appointment_id, assignment_order) DO NOTHING;

INSERT INTO appointment_installed_devices (appointment_id, device_order, alias, mac_address, device_type)
SELECT a.id, d.device_order, d.alias, d.mac_address, d.device_type
FROM appointments a
CROSS JOIN (VALUES
    (0, 'Microfono dormitorio', 'A4:C1:38:5E:01:01', 'MICROPHONE'),
    (1, 'Luz dormitorio', 'A4:C1:38:5E:01:02', 'ACTUATOR'),
    (2, 'Edge de microfono', 'A4:C1:38:5E:01:03', 'HUB'),
    (3, 'Edge de actuadores', 'A4:C1:38:5E:01:04', 'HUB')
) AS d(device_order, alias, mac_address, device_type)
WHERE a.draft_id = '00000000-0000-4000-9000-000000000301' AND a.service_type = 'INSTALLATION'
ON CONFLICT (appointment_id, device_order) DO NOTHING;

-- Inventario y registro técnico del hogar instalado.
INSERT INTO devices (created_at, updated_at, business_id, mac_address, asset_status, alias, device_type)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, b.id, d.mac, 'IN_USE', d.alias, d.type
FROM businesses b
CROSS JOIN (VALUES
    ('A4:C1:38:5E:10:01', 'Microfono dormitorio', 'MICROPHONE'),
    ('A4:C1:38:5E:10:02', 'Luz dormitorio', 'LIGHT'),
    ('A4:C1:38:5E:10:03', 'Sensor de puerta de reserva', 'DOOR'),
    ('A4:C1:38:5E:10:04', 'Cortina de reserva', 'CURTAIN')
) AS d(mac, alias, type)
ON CONFLICT (mac_address) DO UPDATE SET updated_at = CURRENT_TIMESTAMP, alias = EXCLUDED.alias;

INSERT INTO edges (created_at, updated_at, business_id, mac_address, asset_status, edge_type)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, b.id, e.mac, 'IN_USE', e.type
FROM businesses b
CROSS JOIN (VALUES
    ('A4:C1:38:5E:20:01', 'MICROPHONE_EDGE'),
    ('A4:C1:38:5E:20:02', 'ACTUATOR_EDGE')
) AS e(mac, type)
ON CONFLICT (mac_address) DO UPDATE SET updated_at = CURRENT_TIMESTAMP, asset_status = 'IN_USE';

INSERT INTO household_device_registries (
    created_at, updated_at, business_id, household_id, installation_id, status,
    table_version, microphone_edge, actuator_edge
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, b.id, h.id, a.id, 'ACTIVE', 3, me.id, ae.id
FROM businesses b
JOIN households h ON h.alias = 'Hogar demo 01'
JOIN appointments a ON a.draft_id = '00000000-0000-4000-9000-000000000301' AND a.service_type = 'INSTALLATION'
JOIN edges me ON me.mac_address = 'A4:C1:38:5E:20:01'
JOIN edges ae ON ae.mac_address = 'A4:C1:38:5E:20:02'
ON CONFLICT (household_id) DO UPDATE SET
    updated_at = CURRENT_TIMESTAMP, status = 'ACTIVE', table_version = 3,
    microphone_edge = EXCLUDED.microphone_edge, actuator_edge = EXCLUDED.actuator_edge;

INSERT INTO device_assignments (
    created_at, updated_at, device_id, household_id, room_key, device_function,
    assigned_by, assigned_at, installation_id, registry_id
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, d.id, h.id, 'main-bedroom', x.device_function,
       admin.id, CURRENT_TIMESTAMP - interval '10 days', a.id, r.id
FROM households h
JOIN household_device_registries r ON r.household_id = h.id
JOIN appointments a ON a.id = r.installation_id
CROSS JOIN users admin
JOIN (VALUES
    ('A4:C1:38:5E:10:01', 'VOICE_INPUT'), ('A4:C1:38:5E:10:02', 'MAIN_LIGHT')
) AS x(mac, device_function) ON TRUE
JOIN devices d ON d.mac_address = x.mac
WHERE h.alias = 'Hogar demo 01' AND admin.email = 'admin@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM device_assignments da WHERE da.device_id = d.id AND da.unassigned_at IS NULL);

INSERT INTO edge_credentials (
    created_at, updated_at, edge_id, edge_kind, secret_hash, last_used_at, issued_at, issued_by
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, e.id,
       CASE e.edge_type WHEN 'MICROPHONE_EDGE' THEN 'MICROPHONE' ELSE 'ACTUATOR' END,
       encode(sha256(convert_to('alivia-demo-edge-' || e.id::text, 'UTF8')), 'hex'),
       CURRENT_TIMESTAMP - interval '5 minutes', CURRENT_TIMESTAMP - interval '10 days', u.id
FROM edges e CROSS JOIN users u
WHERE e.mac_address IN ('A4:C1:38:5E:20:01', 'A4:C1:38:5E:20:02') AND u.email = 'admin@alivia.test'
ON CONFLICT (edge_id) DO UPDATE SET last_used_at = EXCLUDED.last_used_at, revoked_at = NULL;

INSERT INTO edge_sync_states (edge_id, installation_id, applied_version, acked_at, last_seen_at)
SELECT e.id, r.installation_id, r.table_version, CURRENT_TIMESTAMP - interval '5 minutes',
       CURRENT_TIMESTAMP - interval '1 minute'
FROM edges e
JOIN household_device_registries r ON e.id IN (r.microphone_edge, r.actuator_edge)
WHERE r.household_id = (SELECT id FROM households WHERE alias = 'Hogar demo 01')
ON CONFLICT (edge_id) DO UPDATE SET
    installation_id = EXCLUDED.installation_id, applied_version = EXCLUDED.applied_version,
    acked_at = EXCLUDED.acked_at, last_seen_at = EXCLUDED.last_seen_at;

-- Incidencia completa con evidencia e historial.
INSERT INTO support_incidents (
    created_at, updated_at, household_id, draft_id, device_id, reported_by,
    description, severity, status, resolution, occurred_at, device_alias,
    device_mac, device_type, problem_type
)
SELECT CURRENT_TIMESTAMP - interval '3 days', CURRENT_TIMESTAMP - interval '1 day', h.id::text,
       '00000000-0000-4000-9000-000000000301', d.id, u.id,
       'La luz dejo de responder durante unos minutos.', 'MEDIUM', 'RESOLVED',
       'Se reinicio el actuador y se verifico su conectividad.', CURRENT_TIMESTAMP - interval '3 days',
       d.alias, d.mac_address, d.device_type, 'CONNECTIVITY'
FROM households h JOIN users u ON u.email = 'familiar01@alivia.test'
JOIN devices d ON d.mac_address = 'A4:C1:38:5E:10:02'
WHERE h.alias = 'Hogar demo 01'
  AND NOT EXISTS (SELECT 1 FROM support_incidents WHERE draft_id = '00000000-0000-4000-9000-000000000301');

INSERT INTO support_incident_evidences (incident_id, evidence_order, url, public_id, attached_at)
SELECT i.id, 0, 'https://example.test/evidence/demo-light.jpg', 'seed-demo-light-evidence',
       CURRENT_TIMESTAMP - interval '3 days'
FROM support_incidents i WHERE i.draft_id = '00000000-0000-4000-9000-000000000301'
ON CONFLICT (incident_id, evidence_order) DO NOTHING;

INSERT INTO support_incident_status_histories (incident_id, change_order, status, changed_at)
SELECT i.id, h.change_order, h.status, CURRENT_TIMESTAMP - h.age
FROM support_incidents i
CROSS JOIN (VALUES
    (0, 'OPEN', interval '3 days'), (1, 'SCHEDULED', interval '2 days'),
    (2, 'IN_PROGRESS', interval '30 hours'), (3, 'RESOLVED', interval '1 day')
) AS h(change_order, status, age)
WHERE i.draft_id = '00000000-0000-4000-9000-000000000301'
ON CONFLICT (incident_id, change_order) DO NOTHING;

-- Auditorias representativas de IAM/HCM, contratos y Stripe.
INSERT INTO employee_change_logs (created_at, updated_at, business_id, changes, employee_id)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, e.business_id,
       '{"source":"seed-demo","changes":{"status":{"from":"SUSPENDED","to":"ACTIVE"}}}'::jsonb, e.id
FROM employees e JOIN users u ON u.id = e.user_id
WHERE u.email = 'ana.torres@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM employee_change_logs l WHERE l.employee_id = e.id AND l.changes->>'source' = 'seed-demo');

INSERT INTO contract_versions (
    created_at, updated_at, action, actor_user_id, contract_id, snapshot, version
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'CREATED', admin.id, c.id,
       jsonb_build_object('source', 'seed-demo', 'code', c.code, 'status', c.status), 1
FROM contracts c CROSS JOIN users admin
WHERE c.code = 'CTR-DEMO-TECH-001' AND admin.email = 'admin@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM contract_versions v WHERE v.contract_id = c.id AND v.version = 1);

INSERT INTO stripe_webhook_events (id, event_type, payload, received_at)
VALUES ('evt_demo_subscription_301', 'invoice.payment_succeeded',
        '{"id":"evt_demo_subscription_301","livemode":false,"source":"seed-demo"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Communications: estados variados para probar bandejas y reintentos.
INSERT INTO alerts (
    recipient_user_id, help_request_id, title, body, status, sent_at, attempts, created_at, updated_at
)
SELECT u.id, 'help-demo-001', 'Solicitud de ayuda',
       'La persona asistida solicito apoyo desde el dormitorio.', 'SENT',
       CURRENT_TIMESTAMP - interval '20 minutes', 1, CURRENT_TIMESTAMP - interval '20 minutes', CURRENT_TIMESTAMP
FROM users u WHERE u.email = 'familiar01@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM alerts WHERE help_request_id = 'help-demo-001' AND recipient_user_id = u.id);

INSERT INTO notifications (
    recipient_user_id, type, source_id, title, body, status, read_at,
    push_status, push_attempts, created_at, updated_at
)
SELECT u.id, 'CARE_HANDOVER', 'handover-demo-001', 'Relevo aceptado',
       'Lucia acepto el relevo del turno de la tarde.', 'UNREAD', NULL,
       'SENT', 1, CURRENT_TIMESTAMP - interval '15 minutes', CURRENT_TIMESTAMP
FROM users u WHERE u.email = 'cuidador01@alivia.test'
  AND NOT EXISTS (SELECT 1 FROM notifications WHERE source_id = 'handover-demo-001' AND recipient_user_id = u.id);

INSERT INTO emails (email_address, template, status, sent_at, created_at, updated_at)
SELECT 'nuevo.cuidador@alivia.test', 'CAREGIVER_INVITATION', 'SENT',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM emails WHERE email_address = 'nuevo.cuidador@alivia.test' AND template = 'CAREGIVER_INVITATION');

INSERT INTO push_devices (user_id, token, platform, created_at, updated_at)
SELECT u.id, 'seed-demo-android-token-familiar01', 'ANDROID', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u WHERE u.email = 'familiar01@alivia.test'
ON CONFLICT (token) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- Reserva vigente para mostrar disponibilidad bloqueada en contrataciones.
INSERT INTO evaluation_slot_holds (
    created_at, updated_at, draft_id, technician_id, technician_name,
    scheduled_date, start_time, end_time, expires_at
)
SELECT CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '00000000-0000-4000-9000-000000000204',
       e.id, 'Bruno Silva', CURRENT_DATE + 2, TIME '14:00', TIME '16:00', CURRENT_TIMESTAMP + interval '30 minutes'
FROM employees e JOIN users u ON u.id = e.user_id
WHERE u.email = 'bruno.silva@alivia.test'
ON CONFLICT (draft_id) DO UPDATE SET expires_at = EXCLUDED.expires_at, updated_at = CURRENT_TIMESTAMP;

COMMIT;

SELECT
    (SELECT count(*) FROM plans) AS plans,
    (SELECT count(*) FROM subscriptions) AS subscriptions,
    (SELECT count(*) FROM appointments) AS appointments,
    (SELECT count(*) FROM devices) AS devices,
    (SELECT count(*) FROM edges) AS edges,
    (SELECT count(*) FROM support_incidents) AS incidents,
    (SELECT count(*) FROM care_shifts) AS shifts,
    (SELECT count(*) FROM care_handovers) AS handovers;
