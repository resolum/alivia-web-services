# Simulacro de restauración

`P-483.2` (AP-483) define la plantilla de evidencia y registra un simulacro de restauración
con los scripts de `P-483.1`. El objetivo es demostrar que un respaldo generado con
`scripts/db/backup.ps1` se puede restaurar en otra base sin perder datos ni el historial de
Flyway.

- Guía operativa: `docs/operations/backup-restore.md`.
- Scripts: `scripts/db/backup.ps1` y `scripts/db/restore.ps1`.

## Plantilla de evidencia

Copia esta tabla en cada simulacro y complétala con los valores reales:

| Campo                            | Valor |
|----------------------------------|-------|
| Fecha y hora UTC                 |       |
| Responsable                      |       |
| Commit o versión de los scripts  |       |
| Servidor PostgreSQL              |       |
| Cliente `pg_dump` / `pg_restore` |       |
| Base de origen                   |       |
| Base de destino                  |       |
| Archivo de respaldo              |       |
| Tamaño del respaldo              |       |
| Duración del respaldo            |       |
| Duración de la restauración      |       |
| Tablas restauradas               |       |
| Filas totales                    |       |
| `flyway_schema_history`          |       |
| Conteos de tablas críticas       |       |
| Verificación adicional           |       |
| Resultado                        |       |
| Incidencias                      |       |

## Cómo repetir el simulacro

1. Crea una base de destino vacía y distinta de la de origen:

   ```powershell
   psql -h localhost -p 5432 -U postgres -d postgres -c "create database alivia_drill;"
   ```

2. Genera el respaldo:

   ```powershell
   $env:DB_URL = 'jdbc:postgresql://localhost:5432/alivia_db'
   $env:DB_USERNAME = 'postgres'
   $env:DB_PASSWORD = 'postgres'
   pwsh scripts/db/backup.ps1
   ```

3. Restaura en la base de destino:

   ```powershell
   $env:DB_URL = 'jdbc:postgresql://localhost:5432/alivia_drill'
   pwsh scripts/db/restore.ps1 -BackupFile .\backups\alivia_db_<fecha>.dump
   ```

4. Compara el historial de Flyway y los conteos exactos por tabla:

   ```sql
   select table_name,
          (xpath('/row/c/text()',
                 query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name),
                              false, true, '')))[1]::text::int as filas
   from information_schema.tables
   where table_schema = 'public' and table_type = 'BASE TABLE'
   order by table_name;
   ```

   Para una comprobación más fuerte, compara un volcado `pg_dump --data-only` de ambas
   bases, como se hizo en el registro siguiente.

5. Completa la plantilla y guarda el registro en este documento o en el informe del
   simulacro correspondiente.

## Registro del simulacro

| Campo | Valor |
|---|---|
| Fecha y hora UTC | 2026-10-28T01:08:23Z — 2026-10-28T01:08:24Z (2026-10-27 en hora local) |
| Responsable | Nicolás Juárez (responsable de la subtarea); ejecución asistida |
| Commit o versión de los scripts | Scripts de `P-483.1` en `scripts/db/`, sin commit al ejecutar el simulacro |
| Servidor PostgreSQL | PostgreSQL 18.6 en Docker, `localhost:5433` |
| Cliente `pg_dump` / `pg_restore` | 18.1 (`C:\Program Files\PostgreSQL\18\bin`) |
| Base de origen | `alivia_backup_src` |
| Base de destino | `alivia_drill_20261028` |
| Archivo de respaldo | `backups/alivia_backup_src_20261028T010823489Z.dump` |
| Tamaño del respaldo | 75,704 bytes |
| Duración del respaldo | 0.1 s (script) / 0.41 s (tiempo de pared) |
| Duración de la restauración | 0.4 s (script) / 0.64 s (tiempo de pared) |
| Tablas restauradas | 25 |
| Filas totales | 50 |
| `flyway_schema_history` | Versiones 1 y 2, mismos checksums, `success=true` |
| Conteos de tablas críticas | Idénticos (ver tabla siguiente) |
| Verificación adicional | Secuencias, JSONB y volcado `--data-only` idénticos |
| Resultado | Exitoso, sin pérdida de datos |
| Incidencias | Ninguna |

La duración total, desde la creación de la base de destino hasta el final de la
restauración, fue de 1.25 s. Las comparaciones posteriores no se incluyen en ese tiempo.

### Entorno y datos

- Shell: Windows PowerShell 5.1 (no hay `pwsh` en el equipo).
- Migraciones aplicadas en origen: `V1__baseline.sql` y `V2__constraints_and_indexes.sql`.
- Datos: 4 usuarios, 3 perfiles de persona, 1 perfil de personal, 1 negocio, 1 empleado,
  1 contrato, 2 versiones de contrato, 1 horario con 3 turnos, 2 membresías de cuidado,
  2 actividades, 2 asignaciones, 2 registros de actividad, 1 cambio de empleado,
  1 suscripción, 1 pago y 1 borrador de configuración con 1 habitación.
- Los datos son sintéticos y se insertaron con un script local no versionado.

### Conteos de tablas críticas

| Tabla | Origen | Destino | Coincide |
|---|---:|---:|---|
| `users` | 4 | 4 | Sí |
| `person_profiles` | 3 | 3 | Sí |
| `staff_profiles` | 1 | 1 | Sí |
| `business_profiles` | 1 | 1 | Sí |
| `employees` | 1 | 1 | Sí |
| `contracts` | 1 | 1 | Sí |
| `contract_versions` | 2 | 2 | Sí |
| `work_schedules` | 1 | 1 | Sí |
| `work_schedule_shifts` | 3 | 3 | Sí |
| `care_memberships` | 2 | 2 | Sí |
| `care_activities` | 2 | 2 | Sí |
| `activity_assignments` | 2 | 2 | Sí |
| `activity_logs` | 2 | 2 | Sí |
| `employee_change_logs` | 1 | 1 | Sí |
| `subscriptions` | 1 | 1 | Sí |
| `payments` | 1 | 1 | Sí |
| `flyway_schema_history` | 2 | 2 | Sí |

Los otros 8 conteos de tablas también coinciden. El total es 50 filas en ambas bases.

### Historial de Flyway

| `installed_rank` | Versión | Descripción | Checksum | `success` |
|---:|---|---|---:|---|
| 1 | 1 | baseline | 1028790901 | `true` |
| 2 | 2 | constraints and indexes | -855828550 | `true` |

### Verificaciones adicionales

| Comprobación | Resultado |
|---|---|
| Secuencias (16) | Mismos `last_value` en origen y destino |
| JSONB `contract_versions.snapshot` | `weeklyHours` = `40` y `44` |
| JSONB `business_profiles.business_hours` | `monday.open` = `08:00` |
| Volcado `pg_dump --data-only` | SHA-256 idéntico tras excluir los tokens `\restrict` aleatorios |
| SHA-256 del volcado de datos filtrado | `4E89D4C24D0CF5F036F07471156C41BF4F60FB415FF65ECA6390524C5F5448E6` |

## Limitaciones

- **Datos sintéticos:** el simulacro valida el mecanismo de respaldo y restauración, no el
  volumen ni la distribución de una base de producción. Los tiempos no son un RTO
  representativo.
- **Retención y destino:** siguen sin definir. El simulacro no decide cuántos respaldos
  conservar ni dónde almacenarlos.
- **Cifrado:** el archivo de respaldo no está cifrado.
- **MongoDB:** no forma parte del simulacro.
- **Repetición:** el script que inserta los datos sintéticos no está versionado; para
  repetir el simulacro con los mismos conteos hay que recrear datos equivalentes.
- **Periodicidad:** no hay un calendario definido para repetir el simulacro.
