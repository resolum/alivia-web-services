# Integridad de datos

Este documento es la matriz de `P-482.1` (AP-482): qué restricciones e índices
faltaban, dónde se añadieron y por qué algunas relaciones se dejaron sin clave
foránea. El DDL de esa primera pasada está en
`src/main/resources/db/migration/V2__constraints_and_indexes.sql`. Las tablas de dispositivos,
registros y edge (`V5` a `V15`, AP-68) tienen su propia sección,
[Dispositivos, registros y edge](#dispositivos-registros-y-edge-assets).

## Punto de partida

`V1__baseline.sql` contiene exactamente lo que Hibernate declara para las entidades:
claves primarias, las `@UniqueConstraint`, los `CHECK` de los enums y los pocos índices
anotados con `@Index`. Hibernate **no crea claves foráneas para columnas `Long`** y solo
crea los índices que se anotan, así que faltaban:

- la integridad referencial entre agregados y contextos;
- los índices de las consultas `existsBy*`, de la agenda de cuidado y de la paginación.

`V2` solo añade objetos: no crea, elimina ni altera tablas ni columnas.

## Claves foráneas añadidas

| Tabla.columna                                 | Referencia                | Nulable | Motivo                                                                                              |
|-----------------------------------------------|---------------------------|---------|-----------------------------------------------------------------------------------------------------|
| `care_memberships.user_id`                    | `users.id`                | No      | La membresía pertenece al usuario autenticado que la crea.                                          |
| `care_memberships.member_person_profile_id`   | `person_profiles.id`      | No      | Perfil del cuidador o familiar.                                                                     |
| `care_memberships.assisted_person_profile_id` | `person_profiles.id`      | No      | Perfil de la persona asistida.                                                                      |
| `care_activities.assisted_person_profile_id`  | `person_profiles.id`      | No      | Actividad de una persona asistida.                                                                  |
| `activity_assignments.caregiver_id`           | `care_memberships.id`     | Sí      | Es el **id de la membresía del cuidador**, no `users.id`; `NULL` significa que la hace el familiar. |
| `activity_logs.assignment_id`                 | `activity_assignments.id` | No      | El registro corresponde a una asignación concreta.                                                  |
| `activity_logs.care_activity_id`              | `care_activities.id`      | No      | Copia de la actividad en el momento del registro.                                                   |
| `activity_logs.assisted_person_profile_id`    | `person_profiles.id`      | No      | Copia de la persona asistida.                                                                       |
| `activity_logs.assigned_caregiver_id`         | `care_memberships.id`     | Sí      | Copia del cuidador en vigor ese día; `NULL` si lo hizo el familiar.                                 |
| `activity_logs.completed_by`                  | `care_memberships.id`     | Sí      | Membresía del cuidador que registró el resultado; `NULL` si lo registró un familiar.                |
| `employees.user_id`                           | `users.id`                | No      | El empleado se crea con un usuario IAM (`registerCompanyUser`).                                     |
| `employees.staff_profile_id`                  | `staff_profiles.id`       | No      | El empleado se crea con un `StaffProfile`.                                                          |
| `contracts.employee_id`                       | `employees.id`            | No      | Un contrato siempre pertenece a un empleado existente.                                              |
| `contract_versions.contract_id`               | `contracts.id`            | No      | Historial de versiones del contrato.                                                                |
| `contract_versions.actor_user_id`             | `users.id`                | No      | `Principal#getName()` es el id de usuario IAM.                                                      |
| `work_schedules.employee_id`                  | `employees.id`            | No      | El horario pertenece a un empleado.                                                                 |
| `work_schedules.contract_id`                  | `contracts.id`            | No      | El horario se valida contra el contrato del empleado.                                               |
| `work_schedules.last_modified_by`             | `users.id`                | Sí      | Último actor que modificó el horario.                                                               |
| `employee_change_logs.employee_id`            | `employees.id`            | No      | Auditoría de cambios de un empleado.                                                                |

No se define `ON DELETE`/`ON UPDATE`: la aplicación no borra estos agregados y el
comportamiento por defecto (`NO ACTION`) es el más seguro.

## Índices añadidos

| Índice                                        | Tabla (columnas)                                       | Consulta que lo justifica                                                       |
|-----------------------------------------------|--------------------------------------------------------|---------------------------------------------------------------------------------|
| `idx_person_profiles_email`                   | `person_profiles(email)`                               | `PersonProfileRepository.existsByEmailAddress` (registro y creación de perfil). |
| `idx_staff_profiles_email`                    | `staff_profiles(email)`                                | `StaffProfileRepository.existsByEmailAddress`.                                  |
| `idx_business_profiles_email`                 | `business_profiles(email)`                             | `BusinessProfileRepository.existsByEmailAddress`.                               |
| `idx_business_profiles_ruc`                   | `business_profiles(ruc)`                               | `existsByRuc`.                                                                  |
| `idx_business_profiles_business_name`         | `business_profiles(business_name)`                     | `existsByBusinessName`.                                                         |
| `idx_business_profiles_legal_name`            | `business_profiles(name)`                              | `existsByLegalName`; `LegalName` se mapea a la columna `name` en `V1`.          |
| `idx_care_memberships_assisted_person_status` | `care_memberships(assisted_person_profile_id, status)` | `findAllByAssistedPersonProfileIdInAndStatus`.                                  |
| `idx_care_activities_assisted_person`         | `care_activities(assisted_person_profile_id)`          | `findAllByAssistedPersonProfileIdIn` (agenda).                                  |
| `idx_activity_assignments_care_activity`      | `activity_assignments(care_activity_id)`               | `join fetch` de asignaciones y `findByAssignmentId`.                            |
| `idx_employees_business`                      | `employees(business_id)`                               | Listado paginado de `EmployeeQueryServiceImpl`, siempre filtrado por negocio.   |
| `idx_payments_subscription`                   | `payments(subscription_id)`                            | `findFirstBySubscription_IdAndStripeInvoiceIdIsNullOrderByIdDesc`.              |

`idx_payments_subscription` documenta una consulta ya escrita en `PaymentRepository`,
aunque ese repositorio todavía no se use desde un servicio de producción.

## Ya cubierto: no se duplica

| Columnas                                                                                                                 | Objeto existente en `V1`                                     | Uso                                                                                                  |
|--------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `activity_logs(assignment_id, occurrence_date)`                                                                          | `uk_activity_logs_assignment_date`                           | Búsqueda de registros por asignación y fecha.                                                        |
| `contract_versions(contract_id, version)`                                                                                | `idx_contract_version`                                       | Historial de versiones.                                                                              |
| `contracts(business_id)`                                                                                                 | `idx_contract_business`                                      | Filtro por negocio.                                                                                  |
| `contracts(employee_id, status)`                                                                                         | `idx_contract_employee_status`                               | Contratos por empleado y estado.                                                                     |
| `work_schedules(business_id)`                                                                                            | `idx_work_schedule_business`                                 | Filtro por negocio.                                                                                  |
| `work_schedules(employee_id, status)`                                                                                    | `idx_work_schedule_employee_status`                          | Horarios por empleado y estado.                                                                      |
| `employees(user_id)`                                                                                                     | `uk_employees_user_id`                                       | Un usuario por empleado.                                                                             |
| `employees(staff_profile_id)`                                                                                            | `uk_employees_staff_profile_id`                              | Un perfil de personal por empleado.                                                                  |
| `users(email, account_id, refresh_token_hash, email_verification_token_hash)`                                            | `uk_users_*`                                                 | Unicidad de IAM.                                                                                     |
| `subscriptions(active_household_id, idempotency_key)`                                                                    | `uk4g8qw813836gabamqcuyfbfev`, `uk5rfjs2je5h6fvqnnvpb0cnqqu` | Unicidad de suscripción.                                                                             |
| `plans(code)`                                                                                                            | `ukbsiq2g7uq9l49v27bsijicsys`                                | Código de plan.                                                                                      |
| `payments(stripe_payment_intent_id)`                                                                                     | `ukpuc8mkpduwb4ws7khxcoo0s3t`                                | Idempotencia de Stripe.                                                                              |
| `care_memberships(user_id, …)`                                                                                           | `uk_care_memberships_user_person_role`                       | El prefijo `user_id` ya sirve a `findAllByUserIdAndStatus`; por eso no se añade `(user_id, status)`. |
| `configuration_draft_rooms(draft_id, display_order)`                                                                     | PK compuesta                                                 | Habitaciones de un borrador.                                                                         |
| `plan_features`, `plan_included_devices`, `plan_device_monthly_prices`, `subscription_actuators`, `work_schedule_shifts` | PK compuestas                                                | Ya incluyen la columna de unión como prefijo.                                                        |

## Relaciones que se dejan sin clave foránea

| Columna(s)                                                                                                         | Motivo                                                                                                                                                                                                                                                                                                         |
|--------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `users.profile_id`                                                                                                 | Es polimórfica: apunta a `person_profiles` o a `staff_profiles` según el rol. Una FK simple no puede expresarlo y el usuario se inserta antes de crear el perfil.                                                                                                                                              |
| `employees.business_id`, `contracts.business_id`, `work_schedules.business_id`, `employee_change_logs.business_id` | El contexto `business` es un esqueleto vacío y `businessId` llega en la petición; el código solo lo compara con `employee.businessId`, no comprueba que exista un `business_profile`. Convertirlo en FK exige decidir si el negocio será `business_profiles.id` o una tabla propia. **Pendiente de decisión.** |
| `subscriptions.user_id`, `subscriptions.assisted_person_id`, `subscriptions.business_id`                           | Ningún servicio de producción crea `Subscription` (`SubscriptionRepository` y `PaymentRepository` no están cableados) y los *value objects* no documentan el destino. **Pendiente hasta implementar el flujo de suscripciones.**                                                                               |
| `subscriptions.household_id`, `subscriptions.active_household_id`                                                  | El hogar no es una tabla de este repositorio.                                                                                                                                                                                                                                                                  |
| `users.account_id`                                                                                                 | Identificador de cuenta externo de IAM.                                                                                                                                                                                                                                                                        |
| `configuration_drafts.stripe_*`, `subscriptions.stripe_*`, `payments.stripe_*`                                     | Identificadores de Stripe.                                                                                                                                                                                                                                                                                     |

## Dispositivos, registros y edge (Assets)

Lo que la base de datos garantiza por sí misma en el contexto Assets (AP-68). Lo que no puede
expresar una restricción lo hace cumplir el servicio, con el bloqueo indicado más abajo.

| Tabla | Garantía | Objeto |
|---|---|---|
| `devices`, `edges` | Un identificador físico (MAC) por activo. | `uk_devices_mac_address`, `uk_edges_mac_address`. Que una MAC no la compartan un dispositivo y un edge abarca dos tablas: lo comprueba el servicio. |
| `devices`, `edges` | Detectar dos cambios simultáneos del mismo activo. | Columna `version` (`@Version`, `V13`): el segundo cambio falla con 409 en vez de pisar al primero. |
| `device_assignments` | Un dispositivo tiene **una sola asignación activa**. | Índice único parcial `uk_device_assignments_active_device` (`WHERE unassigned_at IS NULL`, `V6`); `@Entity` no puede declararlo. |
| `device_assignments` | Una asignación cerrada dice quién, cuándo y por qué, y no termina antes de empezar. | `device_assignments_closed_check` (`V8`). El historial no se borra. |
| `device_assignments` | Pertenece al registro de su hogar. | `fk_device_assignments_registry` e `idx_device_assignments_registry_active` (`V12`). |
| `household_device_registries` | Un registro por hogar y por instalación. | `uk_household_device_registries_household`, `..._installation`. |
| `household_device_registries` | Un edge pertenece a **una** instalación y ocupa un solo lugar. | `uk_household_device_registries_microphone_edge`, `..._actuator_edge` y sus claves foráneas a `edges`. |
| `edge_credentials` | Una credencial por edge; solo se guarda el hash del secreto. | `uk_edge_credentials_edge` (`V14`). Sin clave foránea a `edges`: es del contexto `iam` y ve el edge por su id. |
| `edge_sync_states` | Una fila por edge, que no se puede referir a un edge inexistente ni confirmar una versión negativa. | Clave primaria `edge_id`, `fk_edge_sync_states_edge` y `edge_sync_states_applied_version_check` (`V15`). |

Las tablas de Hibernate se pluralizan siempre, incluso con `@Table(name = ...)`: la tabla es
`edge_sync_states`, no `edge_sync_state`
([`database-migrations.md`](database-migrations.md#3-escribe-el-ddl)).

### Orden de bloqueo

Todo cambio de los dispositivos o los edge de un hogar toma **primero el registro del hogar**
(`PESSIMISTIC_WRITE`) y **después** lee el activo. Con el orden contrario, desasignar un
dispositivo y cambiarle el estado a la vez se bloqueaban entre sí (interbloqueo en 60 de 60
rondas medidas), y asignarlo y retirarlo a la vez dejaba ambas operaciones con éxito y perdía la
retirada (180 de 180). Colocar o quitar un edge, cambiar su tipo o su estado y emitirle una
credencial siguen la misma regla. El `@Version` de los activos es la red de seguridad cuando la
otra operación no toma el registro.

| Cambio | Primero | Después |
|---|---|---|
| Asignar un dispositivo | Registro de la instalación | Dispositivo |
| Desasignar, cambiar estado, MAC o tipo de un dispositivo | Registro que lo tiene asignado | Dispositivo |
| Colocar o quitar un edge | Registro de la instalación | Edge |
| Cambiar estado, MAC o tipo de un edge | Registro que lo tiene asignado | Edge |
| Emitir la credencial de un edge | Edge (bloqueado) | Credencial (en `iam`) |

La confirmación de versión de un edge (`edge_sync_states`) y la anotación de su última
consulta no bloquean nada: son sentencias únicas (`insert ... on conflict do nothing` y
`update ... where applied_version < :v`), de modo que dos confirmaciones simultáneas dejan la
mayor sin errores y leer la tabla nunca espera a una confirmación.

La lectura de la tabla de dispositivos de un edge usa una transacción de solo lectura con
aislamiento `REPEATABLE_READ`: la versión y las entradas salen de la misma foto de la base.

Estas reglas se comprueban contra PostgreSQL real en
`AssetsConcurrencyOnPostgresTest` ([`testing.md`](testing.md)).

## Transacciones (P-482.2)

Límites transaccionales de los servicios de comando. Todas las escrituras de un mismo
comando se confirman o se revierten juntas; las consultas usan transacciones de solo
lectura.

| Comando | Transacción | Tablas | Notas |
|---|---|---|---|
| IAM `UserCommandServiceImpl` (alta, verificación, reenvío, acceso, refresco y cierre de sesión) | `@Transactional` | `users`, `person_profiles`, `staff_profiles` | El alta es todo-o-nada; incluye la foto en Cloudinary y el correo de verificación (ver llamadas externas). |
| Profiles `PersonProfileCommandServiceImpl`, `StaffProfileCommandServiceImpl`, `BusinessProfileCommandServiceImpl` | `@Transactional` | `person_profiles`, `staff_profiles`, `business_profiles` | Alta y actualización de un perfil. |
| HCM `EmployeeCommandServiceImpl` (alta y actualización) | `@Transactional` | `employees`, `employee_change_logs`, `staff_profiles`, `users` | El alta encadena perfiles e IAM, incluido el correo de verificación de IAM; la actualización registra el historial de cambios. |
| HCM `ContractCommandServiceImpl` (alta, actualización, renovación y terminación) | `@Transactional` | `contracts`, `contract_versions` | La prueba de rollback está en `ContractCommandServiceTransactionTest`. |
| HCM `WorkScheduleCommandServiceImpl` (alta, actualización y desactivación) | `@Transactional` | `work_schedules`, `work_schedule_shifts` | Los turnos son parte del agregado. |
| Subscriptions `PlanCommandServiceImpl` (semilla de planes) | `@Transactional` | `plans` y tablas hijas | Inserta cada plan una sola vez. |
| Subscriptions `ConfigurationDraftService` (alta y reemplazo de habitaciones) | `@Transactional` | `configuration_drafts`, `configuration_draft_rooms` | `find` es de solo lectura. |
| Nursing `MedicationAdministrationCommandServiceImpl` | `@Transactional` | `activity_logs` | La unicidad de `uk_activity_logs_assignment_date` se traduce a 409. |
| Subscriptions `StripeWebhookEventHandler` | `@Transactional` | `stripe_webhook_events` | La verificación de firma es local; no hay HTTP. |

Los servicios de consulta de HCM, IAM, Profiles, Subscriptions y Nursing usan
`@Transactional(readOnly = true)`. En Nursing se añadió a `CareActivityQueryServiceImpl`
y `MedicationAdministrationQueryServiceImpl`, que antes dependían del `EntityManager`
abierto por OSIV para resolver colecciones perezosas.

### Llamadas externas dentro de la transacción

| Flujo | Llamada | Situación | Decisión |
|---|---|---|---|
| Alta de cuenta | Subida de foto a Cloudinary y envío SMTP | Dentro de la transacción de alta | Se mantiene para conservar el todo-o-nada. La foto se borra en `afterCompletion` si hay rollback. Mover el correo fuera exige un patrón de outbox o un envío posterior al commit con reintentos; **pendiente de autorización**. |
| Pago de borrador | `prepare`, `preauthorize` y `status` llaman a Stripe | Dentro de `@Transactional` | Mantiene la conexión de base de datos durante la llamada HTTP. Se propone separar la lectura/escritura en transacciones cortas con `TransactionTemplate` y dejar la llamada a Stripe fuera; **pendiente de autorización**. |
| Liberación de preautorizaciones | `releaseExpired` cancela en Stripe dentro del bucle transaccional | Dentro de `@Transactional` | Un fallo a mitad revierte las filas ya guardadas, pero no las cancelaciones ya enviadas a Stripe. Se propone procesar cada borrador en su propia transacción; **pendiente de autorización**. |

### Concurrencia pendiente

- `contract_versions.version` se calcula con `countByContractId + 1`; dos peticiones
  concurrentes sobre el mismo contrato pueden generar la misma versión. `V2` **no**
  convierte `idx_contract_version` en único porque el servicio aún no traduce esa
  violación a 409. Añadirlo requiere una migración `V3` y el mapeo del error;
  **pendiente de decisión**.
- Las FK nuevas no cambian el comportamiento normal; solo rechazan referencias huérfanas.

## Verificación

La suite H2 deshabilita Flyway, así que las migraciones no se ejecutan en ella. Se verifican
contra PostgreSQL real: a mano, con los resultados de
[`database-migrations.md`](database-migrations.md#resultado-de-la-última-verificación), y en cada
ejecución por las pruebas de `integration`, que arrancan la aplicación sobre `V1`..`V15` con
`ddl-auto=validate` (ver [`testing.md`](testing.md)).
