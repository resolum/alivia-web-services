# Integridad de datos

Este documento es la matriz de `P-482.1` (AP-482): qué restricciones e índices
faltaban, dónde se añadieron y por qué algunas relaciones se dejaron sin clave
foránea. El DDL está en
`src/main/resources/db/migration/V2__constraints_and_indexes.sql`.

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

## Transacciones (P-482.2)

Esta matriz deja la columna transaccional para `P-482.2`, que definirá los límites y
la concurrencia. Operaciones que ya usan `@Transactional` y que la migración no cambia:

- alta de empleado: perfil de personal + usuario IAM + fila `employees`;
- contrato: alta/actualización/renovación/terminación + `contract_versions`;
- horario: alta/actualización/activación/desactivación;
- registro del resultado de medicación (la unicidad de `activity_logs` se traduce a 409).

Puntos que `P-482.2` debe revisar:

- `contract_versions.version` se calcula con `countByContractId + 1`; dos peticiones
  concurrentes sobre el mismo contrato pueden generar la misma versión. `V2` **no**
  convierte `idx_contract_version` en único porque el servicio aún no traduce esa
  violación a 409.
- `activity_logs` ya tiene `uk_activity_logs_assignment_date` y el servicio traduce
  `DataIntegrityViolationException` a `MedicationOutcomeAlreadyRecordedException` (409).
- Las FK nuevas no cambian el comportamiento normal; solo rechazan referencias huérfanas.

## Verificación

La suite usa H2 y deshabilita Flyway, así que `V2` no se ejecuta en los tests. Se
verifica contra PostgreSQL real; los resultados de la última ejecución están en
[`database-migrations.md`](database-migrations.md#resultado-de-la-última-verificación).
