-- Seed maestro del backend Alivia para desarrollo local.
--
-- Este es el unico punto de entrada que debe usar reset-local-db.py. Prepara datos
-- coherentes para todos los clientes del backend:
--   * Admin web: personal, contratos, horarios y solicitudes de contratacion.
--   * Users web: cuentas, perfiles, hogares y configuraciones de planes.
--   * Android/iOS: familiares, cuidadores, asistidos, red de cuidado, agenda,
--     recordatorios, administraciones de medicamentos, turnos y relevos.
--   * Operaciones: suscripciones, instalaciones, activos, edge y soporte.
--   * Comunicaciones: alertas, notificaciones, correos y dispositivos push.
--
-- Los archivos incluidos son modulos internos del seed. No deben ejecutarse a mano
-- para preparar el entorno completo; este archivo mantiene el orden de dependencias.
-- psql resuelve \ir con respecto a la ubicacion de este mismo archivo.

\set ON_ERROR_STOP on

\echo '=== 1/5 Catalogo, empresa y administracion ==='
\ir seed-foundation-demo.sql

\echo '=== 2/5 Capital humano y cuentas del panel administrativo ==='
\ir seed-hcm-demo.sql

\echo '=== 3/5 Perfiles, hogares, cuidado y agenda de las aplicaciones ==='
\ir seed-mobile-demo.sql

\echo '=== 4/5 Solicitudes para el panel de contrataciones ==='
\ir seed-hiring-demo.sql

\echo '=== 5/5 Suscripciones, operaciones, activos, edge y soporte ==='
\ir seed-platform-demo.sql

\echo '=== Seed integral de Alivia completado ==='
