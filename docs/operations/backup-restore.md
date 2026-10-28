# Respaldo y restauración de PostgreSQL

`P-483.1` (AP-483) deja dos scripts reproducibles para respaldar y restaurar la base
PostgreSQL de Alivia con `pg_dump` y `pg_restore`. Este documento describe los requisitos,
el uso, las garantías de los scripts y la evidencia del simulacro local.

- `scripts/db/backup.ps1`: genera un respaldo lógico en formato custom.
- `scripts/db/restore.ps1`: restaura ese respaldo en una base de destino.

## Alcance

El respaldo incluye el esquema y los datos de la base indicada en `DB_URL`: tablas,
secuencias, índices, restricciones y el historial de Flyway (`flyway_schema_history`).

No incluye:

- MongoDB, que se administra por separado;
- roles, propietarios y privilegios (el respaldo se hace con `--no-owner` y
  `--no-privileges`);
- configuración del servidor, extensiones instaladas ni archivos fuera de la base;
- respaldos incrementales o point-in-time recovery (solo hay respaldo lógico completo).

## Requisitos

- `pg_dump`, `pg_restore` y `psql` del cliente de PostgreSQL. La versión del cliente debe
  ser igual o superior a la del servidor: `pg_dump` no puede respaldar un servidor más
  nuevo que el cliente.
- Un usuario con permiso de lectura sobre todas las tablas y secuencias para respaldar, y
  con permiso para crear objetos en la base de destino para restaurar.
- PowerShell 5.1 (Windows) o PowerShell 7+ (`pwsh`, también en Linux/macOS). Los scripts
  se guardan en UTF-8 con BOM para que Windows PowerShell 5.1 interprete correctamente los
  acentos; `pwsh` acepta el mismo archivo.
- La base de destino debe existir antes de restaurar. Los scripts no crean bases de datos.

## Variables de entorno

| Variable      | Obligatoria | Uso                                                                 |
|---------------|-------------|---------------------------------------------------------------------|
| `DB_URL`      | Sí          | URL JDBC o libpq, por ejemplo `jdbc:postgresql://localhost:5432/alivia_db`. |
| `DB_USERNAME` | Sí          | Usuario de la base de datos.                                         |
| `DB_PASSWORD` | Sí          | Contraseña. Se pasa a las herramientas mediante `PGPASSWORD`, nunca como argumento. |
| `PG_BIN`      | No          | Directorio de `pg_dump`, `pg_restore` y `psql` si no están en `PATH`. |
| `BACKUP_DIR`  | No          | Directorio de respaldos. Por defecto, `backups/` en la raíz del repositorio. |

No incluyas usuario ni contraseña dentro de `DB_URL`. Los scripts rechazan las URLs con
credenciales embebidas o con parámetros `user`/`password` para evitar que queden en el
historial de la shell, en los logs o en la lista de procesos.

## Respaldo

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/alivia_db'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'postgres'
$env:PG_BIN = 'C:\Program Files\PostgreSQL\18\bin'
pwsh scripts/db/backup.ps1
```

El script:

- escribe un archivo `<base>_<fecha UTC>.dump` con formato custom de `pg_dump`, por
  ejemplo `alivia_db_20261028T005700291Z.dump`;
- usa `--no-owner` y `--no-privileges`, de modo que el respaldo se pueda restaurar con
  otro usuario;
- usa `--lock-wait-timeout=30s`: si no puede tomar los bloqueos necesarios, falla en lugar
  de quedar esperando indefinidamente;
- elimina el archivo parcial si `pg_dump` falla;
- no imprime ni guarda la contraseña, y no la pasa por línea de comandos.

`backups/` está en `.gitignore`. Un `.dump` contiene datos personales: trátalo como
información sensible, no lo adjuntes a incidencias ni lo subas al repositorio.

## Restauración

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/alivia_restore'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = 'postgres'
$env:PG_BIN = 'C:\Program Files\PostgreSQL\18\bin'
pwsh scripts/db/restore.ps1 -BackupFile .\backups\alivia_db_20261028T005700291Z.dump
```

Por defecto, la base de destino debe estar vacía. El script cuenta las tablas existentes y
se detiene si encuentra alguna, para no mezclar el respaldo con datos previos.

Para reemplazar los objetos que contiene el respaldo usa `-Clean`:

```powershell
pwsh scripts/db/restore.ps1 -BackupFile .\backups\alivia_db_20261028T005700291Z.dump -Clean
```

`-Clean` ejecuta `pg_restore --clean --if-exists`, que elimina y vuelve a crear los objetos
del respaldo. Úsalo solo sobre una base desechable o después de verificar el respaldo.

La restauración usa `--single-transaction` y `--exit-on-error`: si un objeto falla, no
queda una restauración parcial. Añade `-Verbose` para ver la salida de `pg_restore`.

La base de destino se puede crear con `psql`:

```powershell
psql -h localhost -p 5432 -U postgres -d postgres -c "create database alivia_restore;"
```

Después de restaurar, arranca la aplicación con `ddl-auto=validate` y Flyway habilitado
para comprobar que el esquema restaurado coincide con las entidades y con el historial de
migraciones.

## Verificación del simulacro

Simulacro ejecutado el 2026-10-27 (hora local) / 2026-10-28 UTC:

| Elemento            | Valor                                                        |
|---------------------|--------------------------------------------------------------|
| Servidor            | PostgreSQL 18.6 en Docker, `localhost:5433`                  |
| Cliente             | `pg_dump`/`pg_restore` 18.1 (`C:\Program Files\PostgreSQL\18\bin`) |
| Base de origen      | `alivia_backup_src`                                          |
| Base de destino     | `alivia_backup_dst`                                          |
| Shell               | Windows PowerShell 5.1 (no hay `pwsh` en el equipo)          |
| Migraciones         | `V1__baseline.sql` y `V2__constraints_and_indexes.sql`       |

Datos de prueba insertados: 4 usuarios, 3 perfiles de persona, 1 perfil de personal, 1
negocio, 1 empleado, 1 contrato, 2 versiones de contrato, 1 horario con 3 turnos, 2
membresías de cuidado, 2 actividades, 2 asignaciones, 2 registros de actividad, 1 cambio de
empleado, 1 suscripción, 1 pago y 1 borrador de configuración con 1 habitación. Se usaron
columnas JSONB (`business_hours`, `snapshot`, `changes`) para comprobar su conservación.

### Conteos antes y después

Los 25 conteos coinciden entre origen y destino:

| Tabla                        | Filas |
|------------------------------|-------|
| `activity_assignments`       | 2     |
| `activity_logs`              | 2     |
| `business_profiles`          | 1     |
| `care_activities`            | 2     |
| `care_memberships`           | 2     |
| `configuration_draft_rooms`  | 1     |
| `configuration_drafts`       | 1     |
| `contract_versions`          | 2     |
| `contracts`                  | 1     |
| `employee_change_logs`       | 1     |
| `employees`                  | 1     |
| `flyway_schema_history`      | 2     |
| `payments`                   | 1     |
| `person_profiles`            | 3     |
| `plan_device_monthly_prices` | 4     |
| `plan_features`              | 9     |
| `plan_included_devices`      | 3     |
| `plans`                      | 2     |
| `staff_profiles`             | 1     |
| `stripe_webhook_events`      | 0     |
| `subscription_actuators`     | 0     |
| `subscriptions`              | 1     |
| `users`                      | 4     |
| `work_schedule_shifts`       | 3     |
| `work_schedules`             | 1     |
| **Total**                    | **50** |

### Resultados

| Comprobación                                            | Resultado |
|---------------------------------------------------------|-----------|
| `backup.ps1` sobre `alivia_backup_src`                  | Archivo de 75,704 bytes en 0.2 s |
| `restore.ps1` sobre una base vacía                      | 25 tablas restauradas en 0.3 s |
| Conteos de las 25 tablas                                | Idénticos |
| `flyway_schema_history`                                 | Versiones 1 y 2, mismos checksums y `success=true` |
| Secuencias (16)                                         | Mismos `last_value` en origen y destino |
| Restricciones e índices                                 | 262 restricciones y 58 índices en ambas bases |
| JSONB                                                   | `snapshot->>'weeklyHours'` devuelve `40` y `44`; `business_hours->'monday'->>'open'` devuelve `08:00` |
| Volcado solo de datos (`pg_dump --data-only`)           | SHA-256 idéntico tras excluir los tokens `\restrict` aleatorios de `pg_dump` 18 |
| Arranque de la aplicación contra la base restaurada     | Flyway: versión 2 y "up to date"; Hibernate `validate`; `/actuator/health` = `UP` |
| Restauración sobre base con tablas sin `-Clean`         | Exit 1 con mensaje de protección |
| Restauración con `-Clean` sobre la misma base           | Exit 0, 25 tablas |
| `backup.ps1` con contraseña incorrecta                  | Exit 1 y sin archivo parcial |
| `backup.ps1` sin `DB_URL`                               | Exit 1 |
| `backup.ps1` con credenciales dentro de `DB_URL`        | Exit 1 |
| `restore.ps1` con un archivo inexistente                | Exit 1 |

El SHA-256 del volcado de datos filtrado fue
`4E89D4C24D0CF5F036F07471156C41BF4F60FB415FF65ECA6390524C5F5448E6` en origen y destino.

## Limitaciones y pendientes

- **Retención y destino:** no hay política definida de cuántos respaldos conservar, durante
  cuánto tiempo, ni dónde almacenarlos fuera del equipo. Es una decisión pendiente; los
  scripts no la inventan ni la aplican.
- **Programación:** los scripts se ejecutan manualmente. No hay tarea programada ni
  integración con CI.
- **Cifrado:** el `.dump` no está cifrado ni protegido con contraseña. Protégelo con los
  permisos del sistema de archivos y, si se saca del equipo, con cifrado en reposo.
- **RPO/RTO:** al ser un respaldo lógico completo, el punto de recuperación es el último
  respaldo. No hay WAL ni recuperación a un instante concreto.
- **MongoDB:** queda fuera del alcance de estos scripts.
- **Propietarios y privilegios:** al usar `--no-owner` y `--no-privileges`, los objetos
  restaurados pertenecen al usuario que ejecuta `pg_restore` y los `GRANT` no se
  reproducen. Si el entorno de destino depende de roles concretos, hay que aplicarlos
  aparte.
- **Verificación del archivo:** los scripts no ejecutan `pg_restore --list` antes de
  restaurar. La restauración con `--exit-on-error` detecta un archivo corrupto o
  incompatible.

## Referencias

- `docs/database-migrations.md`: convenciones de Flyway y verificación del esquema.
- `docs/local-environment.md`: arranque local y credenciales de `compose.yaml`.
- `src/main/resources/db/migration/`: migraciones que se incluyen en el respaldo.
