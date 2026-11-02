# Migraciones de base de datos

## Estado actual

Flyway es el dueño del esquema. Las migraciones viven en
`src/main/resources/db/migration` y se aplican al arrancar la aplicación.

| Elemento | Valor |
|---|---|
| Migraciones existentes | `V1__baseline.sql` — esquema completo (24 tablas, 235 columnas, 234 constraints, 45 índices) · `V2__constraints_and_indexes.sql` — 19 claves foráneas y 11 índices |
| `ddl-auto` en `dev` | `update` (Hibernate puede crear tablas que falten; úsalo solo en local) |
| `ddl-auto` en `prod` | `validate` — Flyway crea el esquema y Hibernate solo lo comprueba al arrancar |
| `ddl-auto` en `test` | El del perfil de pruebas; Flyway está deshabilitado (ver más abajo) |
| Historial | Tabla `flyway_schema_history` |

`V1` no es un marcador: contiene el DDL completo que Hibernate genera para las
entidades JPA actuales. Se capturó con el procedimiento de la sección
[Cómo se generó `V1`](#cómo-se-generó-v1).

`V2` añade la integridad referencial y los índices de búsqueda que Hibernate no
declara. La matriz completa, con lo que se añadió y lo que se dejó fuera a propósito,
está en [`data-integrity.md`](data-integrity.md).

## Configuración

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
```

`baseline-on-migrate=true` permite adoptar Flyway sobre una base que ya tiene tablas
creadas por Hibernate: Flyway registra una fila `BASELINE` con versión 1 y **no ejecuta
`V1`**. Esa base debe tener ya el esquema de `V1`; si le falta algo, `validate` lo detecta
en el arranque.

## Perfil de pruebas

El perfil `test` usa H2 en memoria y **deshabilita Flyway**
(`spring.flyway.enabled=false` en `src/test/resources/application-test.properties`).
El DDL de las migraciones es de PostgreSQL (columnas `identity`, `jsonb`, `CHECK` con
`ARRAY[...]`) y H2 no puede ejecutarlo. Por eso las pruebas construyen el esquema con
Hibernate y la paridad de las migraciones se verifica contra PostgreSQL real, no en la
suite. `FlywayMigrationTest` solo valida convenciones de los archivos (nombre, versión
única, `V1` no vacío, ausencia de artefactos de `pg_dump`) y que `V2` sea aditiva.

## Cómo se generó `V1`

Procedimiento reproducible para volver a capturar el esquema completo:

```powershell
# 1. Base vacía (usa una base desechable, nunca la de desarrollo).
psql -h localhost -p 5432 -U postgres -c "create database alivia_gen;"

# 2. Arranca la aplicación con Flyway apagado para que Hibernate cree el esquema.
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_DOCKER_COMPOSE_ENABLED = "false"
$env:SPRING_FLYWAY_ENABLED = "false"
$env:DB_URL = "jdbc:postgresql://localhost:5432/alivia_gen"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
# Detén la aplicación cuando aparezca "Started AliviaWebServicesApplication".

# 3. Vuelca el esquema.
pg_dump -h localhost -p 5432 -U postgres -d alivia_gen --schema-only --schema=public `
        --no-owner --no-privileges --no-comments -f schema-dump.sql
```

El volcado no se copia tal cual: hay que limpiarlo, porque Flyway no lo aceptaría o
crearía diferencias con lo que genera Hibernate.

1. Elimina `\restrict` y `\unrestrict` (son metacomandos de `psql`).
2. Elimina los `SET ...` y el `SELECT pg_catalog.set_config(...)` iniciales.
3. Elimina `CREATE SCHEMA public;` (el esquema ya existe y lo gestiona Flyway).
4. Quita el prefijo `public.` para que la migración funcione con el esquema por defecto.
5. Reescribe los `CHECK` con `= ANY (ARRAY['A'::character varying, ...]::text[])` como
   `CHECK (columna IN ('A', ...))`. Es equivalente, pero hace que PostgreSQL guarde la
   misma definición que crea Hibernate, y así la comparación de esquemas sale idéntica.
6. Deja solo comentarios `--` de SQL estándar.

## Verificación

### Base limpia

```powershell
psql -h localhost -p 5432 -U postgres -c "create database alivia_flyway;"

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_DOCKER_COMPOSE_ENABLED = "false"
$env:SPRING_FLYWAY_ENABLED = "true"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = "validate"   # obligatorio: detecta divergencias
$env:DB_URL = "jdbc:postgresql://localhost:5432/alivia_flyway"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

En el log debe aparecer:

```
Migrating schema "public" to version "1 - baseline"
Migrating schema "public" to version "2 - constraints and indexes"
Successfully applied 2 migrations to schema "public", now at version v2
Started AliviaWebServicesApplication in ...
```

Si `ddl-auto=validate` encuentra una tabla o columna que la migración no crea, el arranque
falla con `Schema validation: missing table [...]` o `missing column [...]`.

### Base existente (creada por Hibernate antes de Flyway)

Es el caso de las bases que ya están desplegadas. Sobre una base con tablas y sin
historial, el arranque debe registrar el baseline y aplicar solo las migraciones
posteriores a `V1`:

```
Creating Schema History table "public"."flyway_schema_history" with baseline ...
Successfully baselined schema with version: 1
Migrating schema "public" to version "2 - constraints and indexes"
Successfully applied 1 migration to schema "public", now at version v2
```

Con `ddl-auto=validate`, ese arranque además demuestra que el esquema existente coincide
con las migraciones. Si los datos existentes violan una FK nueva, `V2` falla y hay que
corregir las filas huérfanas antes de desplegar.

### Segundo arranque

```
Schema "public" is up to date. No migration necessary.
```

### Resultado de la última verificación

Ejecutada contra PostgreSQL 18.6 con `V1` y `V2`:

| Comprobación | Resultado |
|---|---|
| Base limpia + `V1` + `V2` + `validate` | ✅ 24 tablas y 2 migraciones aplicadas, arranque correcto; `/actuator/health` 200 |
| Base existente con datos (sin historial) + `V2` + `validate` | ✅ `BASELINE` en v1, aplica solo `V2`, arranque correcto, datos conservados (empleados, contratos y registros de actividad) |
| Segundo arranque | ✅ `Schema "public" is up to date. No migration necessary.` |
| `V2` sobre una base con una FK huérfana | ✅ falla con `violates foreign key constraint "fk_care_memberships_user"` en lugar de dejar la fila huérfana |
| Claves foráneas (`pg_constraint`) | ✅ 28 (9 de `V1` + 19 de `V2`) |
| Índices de esquema (`pg_indexes`, sin `flyway_schema_history`) | ✅ 56 (45 de `V1` + 11 de `V2`) |
| Perfil `prod` sobre base limpia | ✅ migra, valida, arranca; `/actuator/health` 200 y `/v3/api-docs` 404 |
| Perfil `prod` sobre base con deriva (columna borrada) | ✅ el arranque falla: `Schema validation: missing column [failed_access_attempts] in table [users]` |
| Columnas (nombre, tipo, longitud, nulabilidad) | ✅ 235/235 idénticas a las de Hibernate |
| Restricciones de `V1` (`pg_constraint`) | ✅ 234/234 idénticas |
| Índices de `V1` (`pg_indexes`) | ✅ 45/45 idénticos |
| `.\mvnw.cmd clean verify` | ✅ 848 pruebas, 0 fallos, 8 *skipped* |

La prueba de deriva es la que justifica `validate` en producción: sin ella, Hibernate habría
seguido arrancando con la columna ausente y el error aparecería en la primera consulta.
La prueba de la FK huérfana es la que justifica que `V2` falle en lugar de aceptar datos
inconsistentes. Las filas de paridad de `V1` y la prueba de deriva se conservan de las
verificaciones de `P-480.2` y `P-464.2`: `V2` no modifica tablas, columnas ni tipos.

## Añadir una entidad nueva al esquema

Regla de oro: **Hibernate no crea el esquema en producción**. Toda tabla, columna,
restricción o índice nuevo necesita su migración en el mismo cambio que la entidad.

### 1. Crea o modifica la entidad JPA

Nada especial: `@Entity`, `@Table`, `@Column`, etc. Los nombres físicos los decide
`SnakeCaseWithPluralizedTablePhysicalNamingStrategy`:

- Tabla: nombre de la clase en snake_case y pluralizado (`CareActivity` → `care_activities`).
- Columna: nombre del campo en snake_case (`assistedPersonProfileId` → `assisted_person_profile_id`).

Si usas `@Table(name = ...)` o `@Column(name = ...)` explícitos, el nombre es el que escribas.

### 2. Reserva el número de versión

```powershell
Get-ChildItem src\main\resources\db\migration
```

El siguiente número es el mayor existente + 1: `V3__add_devices.sql`, `V4__...`. Trae
`develop` antes de crearlo. Si dos ramas crean el mismo `Vn`, la segunda se renombra al
siguiente número libre antes de abrir el PR; nunca se reutiliza un número ya mergeado.

### 3. Escribe el DDL

Un archivo por cambio lógico, en PostgreSQL y con SQL estándar:

```sql
-- V3: registra los dispositivos instalados en cada hogar.
CREATE TABLE household_devices (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    household_id varchar(255) NOT NULL,
    device_type varchar(10) NOT NULL CHECK (device_type IN ('LIGHT', 'DOOR', 'WINDOW', 'CURTAIN')),
    display_order integer NOT NULL CHECK (display_order >= 0)
);

ALTER TABLE household_devices
    ADD CONSTRAINT fk_household_devices_subscription
    FOREIGN KEY (household_id) REFERENCES subscriptions (active_household_id);

CREATE INDEX idx_household_devices_subscription ON household_devices USING btree (household_id);
```

Convenciones:

- Las palabras clave van en mayúsculas, como en `V1` y `V2`.
- Nombra explícitamente las restricciones e índices (`fk_...`, `uk_...`, `idx_...`); los
  nombres autogenerados de Hibernate (`uk4g8qw81...`) solo aparecen cuando no los fijas
  en la entidad, y son difíciles de rastrear.
- Nada de metacomandos de `psql` (`\d`, `\restrict`) ni `create schema`.
- No incluyas `drop table` de tablas con datos salvo que el cambio lo exija y esté revisado.

Si no sabes qué DDL genera Hibernate para tu entidad, usa una base desechable y el
procedimiento de [Cómo se generó `V1`](#cómo-se-generó-v1): arranca con
`SPRING_FLYWAY_ENABLED=false`, deja que Hibernate cree la tabla, vuelca con `pg_dump` y
copia solo las sentencias de la tabla nueva.

### 4. Verifica antes de abrir el PR

```powershell
.\mvnw.cmd clean verify
```

Y después, contra PostgreSQL, los dos caminos:

```powershell
# a) Base limpia: Flyway aplica V1..Vn desde cero y validate debe pasar.
#    (repite el procedimiento de "Base limpia" apuntando a una base vacía)

# b) Actualización: base con el esquema anterior (Vn-1) y datos de prueba.
#    Al arrancar debe aplicar solo Vn y validar:
#    Migrating schema "public" to version "2 - add_devices"
```

La opción (b) es la que detecta los cambios destructivos: una columna `not null` sin
`default` sobre una tabla con filas falla al migrar, y es mejor descubrirlo antes del
despliegue.

### 5. Cuidado con `ddl-auto=update` en local

En `dev` Hibernate sigue creando lo que falte. Si olvidas la migración, la aplicación
arranca igual y el problema aparece en producción con `validate`. Para detectarlo en
local, arranca una vez con la validación activa:

```powershell
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = "validate"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Si falta la migración, el arranque falla indicando la tabla o columna ausente.

### 6. Reglas que no se negocian

- **Una migración aplicada es inmutable.** Flyway guarda un checksum; si el archivo cambia,
  el arranque falla con `Migration checksum mismatch`. Corrige con una migración nueva.
- **Nunca borres ni renombres** un archivo ya mergeado en `develop` o `main`.
- **No edites `V1`** salvo que aún no haya llegado a `develop`/`main`.
- Los cambios de esquema van en el mismo PR que la entidad, no en uno posterior.