# Contrato de consulta de Tracking (para el frontend)

> **Estado:** 2026-11-24, paso T-4.2. **Pendiente de validación con el frontend** (X-T04): los nombres de campo se pueden ajustar antes de que se consuman. Ejemplo ejecutable: `docs/fixtures/tracking/q-home-status.response.json` (lo comprueba `HomeStatusControllerTest`).

## `GET /api/v1/assisted-persons/{personId}/devices/status`

Qué ve la familia de los dispositivos del hogar de una persona asistida: el **estado actual** si se oye al edge, o el **último estado conocido** si no (US-22 escenarios 1 y 3, US-30 escenarios 1 y 3, TS-11).

**Quién puede llamarlo:** una cuenta de usuario de la red de cuidado de la persona (familiar a cargo, cuidador activo) o miembro del hogar con lectura. Reglas y códigos de error en `docs/security/route-access-matrix.md` §6.

### Respuesta `200`

| Campo | Significado |
|---|---|
| `personId`, `householdId` | La persona consultada y su hogar |
| `edgeLink` | `ONLINE` (todos los edges del hogar se oyen), `OFFLINE` (al menos uno no se oye) o `UNKNOWN` (ninguno ha reportado jamás) |
| `offlineSince` | Desde cuándo dejó de oírse el primer edge caído. **Ausente** mientras `edgeLink` no sea `OFFLINE` |
| `lastUpdatedAt` | Lo más reciente que se sabe del hogar (un reporte de un edge o un estado guardado). **Ausente** si nada se ha reportado |
| `mayHavePendingEvents` | `true` si un edge está caído o dijo que tiene eventos por sincronizar: «pueden existir eventos pendientes» |
| `devices[]` | Todos los dispositivos colocados en el hogar, ordenados por `deviceId` |

Cada dispositivo:

| Campo | Significado |
|---|---|
| `deviceId`, `alias`, `type`, `roomKey` | Qué es y dónde está (`type`: `MICROPHONE`, `DOOR`, `WINDOW`, `CURTAIN`, `LIGHT`) |
| `connectionStatus` | `CONNECTED`, `DISCONNECTED` o `UNKNOWN`. **`UNKNOWN` = nunca reportó** |
| `operationalStatus` | `OK`, `DEGRADED` o `FAILED`. Ausente si nunca reportó |
| `currentState` | `open`, `closed`, `on`, `off`…, si el dispositivo lo informa |
| `batteryPct` | 0 a 100, si el dispositivo la mide |
| `lastCommunicationAt`, `observedAt` | Última comunicación del edge con el dispositivo; cuándo se observó este estado |
| `lastUpdatedAt` | Cuándo lo guardó el backend. Ausente si nunca reportó |
| `faults[]` | Códigos de falla; vacío si no hay |
| `stale` | **`true`: el estado mostrado es el último conocido porque su edge no se oye ahora.** No significa que el dispositivo esté desconectado |

Los campos sin valor **se omiten**, no se envían como `null` (salvo las listas, que son `[]`).

### Reglas

- Un dispositivo cuyo edge está caído **no se marca desconectado por inferencia**: conserva su último estado confirmado y `stale=true`. La causa puede ser el enlace del edge y no el dispositivo (US-30 escenario 3).
- `stale` es por dispositivo (según el edge que lo reportó); `edgeLink` resume todo el hogar. Un hogar con dos edges puede tener uno caído y otro no.
- El estado de un dispositivo que estuvo en **otro hogar** no se muestra: solo cuentan los estados guardados para este hogar.
- **Solo cuentan los edges que el hogar tiene ahora** (los que `assets` le asigna): el estado de un edge que se reemplazó o se retiró no hace que el hogar figure `OFFLINE`, ni da `offlineSince`, `mayHavePendingEvents` ni `lastUpdatedAt`. Los dispositivos cuyo último estado vino de ese edge se muestran con ese estado y `stale=true` (nadie lo actualiza). Un hogar sin ningún edge vigente que haya reportado es `UNKNOWN`.
- `lastUpdatedAt` del hogar es el máximo entre el último reporte de cualquiera de sus edges y el último estado guardado.

### Errores

| Estado | Código | Cuándo |
|---|---|---|
| `401` | — | Sin sesión |
| `403` | `access_denied` | La cuenta no es de un usuario (por ejemplo, un edge) |
| `404` | `assisted_person_not_found` | La persona no existe **o** la cuenta no puede leerla (misma respuesta: D-TR-13, confirmada el 2026-11-25) |
| `404` | `assisted_person_without_household` | La cuenta puede leer a la persona, pero esta aún no tiene hogar |
| `503` | `tracking_storage_unavailable` | No se pudo leer el estado (cabecera `Retry-After: 5`). **No** se responde un hogar vacío cuando falla la lectura |

---

## `GET /api/v1/devices/{deviceId}/events`

El historial de un dispositivo, del evento más reciente al más antiguo (TS-11 escenario 2, AP-374). Ejemplo ejecutable: `docs/fixtures/tracking/q-device-events.response.json`.

**Quién puede llamarlo:** quien pueda leer el hogar **donde el dispositivo está colocado ahora** (red de cuidado de alguien que vive allí, o miembro del hogar con lectura). Lo que le pasó al dispositivo en un hogar anterior **no** se muestra.

### Parámetros (todos opcionales)

| Parámetro | Significado |
|---|---|
| `from` | Inicio del período, **incluido** (ISO-8601 en UTC, p. ej. `2026-11-24T00:00:00Z`). Sin él: el período máximo hacia atrás desde `to` |
| `to` | Fin del período, **excluido**. Sin él: ahora |
| `type` | Solo ese tipo de evento (`COMMAND_RESULT`, `HELP_REQUESTED`…; mayúsculas o minúsculas). Sin él: todos |
| `page` | Número de página, **desde 0**. Por defecto 0 |
| `size` | Eventos por página. Por defecto 20, máximo 100 |

**Límites (D-TR-9: valores propuestos, pendientes de confirmar):** un período de hasta **31 días**, páginas de 20 y máximo 100. Se pueden cambiar sin tocar el código con `alivia.tracking.history-max-range`, `history-default-page-size` e `history-max-page-size`.

### Respuesta `200`

`deviceId`, `from`, `to` (el período realmente consultado), `page`, `size`, `totalElements`, `totalPages` e `items[]`. Un período sin eventos es una página vacía, no un error; una página más allá del final también (`totalElements` sigue indicando cuántos hay).

Cada evento: `eventId`, `type`, `severity` (`INFORMATIVE`, `PREVENTIVE`, `IMPORTANT`, `CRITICAL`), `origin` (`LIVE`, o `SYNCED` = ejecutado sin conexión y sincronizado después), `occurredAt`, `receivedAt` y, cuando aplican: `result`, `commandId`, `action` (`OPEN`, `CLOSE`, `ON`, `OFF`), `confidence`, `batteryPct`, `detail`, `attributes`. Los campos sin valor se omiten. No se muestra qué edge ni qué instalación lo reportó. No hay audio ni transcripciones (RN-10).

### Errores

| Estado | Código | Cuándo |
|---|---|---|
| `400` | `invalid_range` | `from` es posterior a `to` |
| `400` | `range_too_large` | El período supera el máximo (31 días propuestos) |
| `400` | `invalid_page` / `invalid_page_size` | `page` negativa; `size` menor que 1 o mayor que el máximo |
| `400` | `invalid_type` | El tipo no existe |
| `400` | `invalid_request` | `from`/`to` no son una fecha ISO o `page`/`size` no son números |
| `401` / `403` | — / `access_denied` | Sin sesión / la cuenta no es de un usuario |
| `404` | `device_not_found` | El dispositivo no existe, no está colocado en un hogar activo, **o** la cuenta no puede leerlo (misma respuesta, D-TR-13 confirmada) |
| `503` | `tracking_storage_unavailable` | No se pudo leer (`Retry-After: 5`); no se responde un historial vacío |

Los parámetros se validan **antes** de consultar nada.

**Orden y paginación:** del más reciente al más antiguo; dos eventos del mismo instante se ordenan por su identificador interno, así que no se repiten ni se saltan de una página a la siguiente. La paginación es por desplazamiento: si llegan eventos nuevos mientras el cliente pagina, los siguientes desplazan los ya vistos y la página siguiente puede repetir uno. Para una consulta puntual no importa; si hace falta un recorrido estable, el cliente fija `to`.

---

## `GET /api/v1/devices/{deviceId}/telemetry`

Las lecturas de diagnóstico de un dispositivo (señal, memoria libre…) para quien cuida las instalaciones (AP-485, TS-47). Ejemplo ejecutable: `docs/fixtures/tracking/q-device-telemetry.response.json`.

**Quién puede llamarlo:** solo cuentas con rol `ADMINISTRATOR` o `TECHNICIAN`. Los familiares y cuidadores **no** (reciben `403`): las métricas internas del equipo no son parte de lo que ve la familia. Los edges tampoco.

Parámetros, paginación, límites (D-TR-9) y errores `400` son **los mismos** que el historial de eventos (`from`, `to`, `page`, `size`; sin `type`). La respuesta es la misma página (`deviceId`, `from`, `to`, `page`, `size`, `totalElements`, `totalPages`, `items[]`), con cada lectura como `{edgeId, sampledAt, metrics}`; `metrics` es un mapa libre porque cada dispositivo reporta lo suyo.

- Solo se devuelven las lecturas del hogar donde el dispositivo está colocado **ahora**. Un dispositivo que no está colocado en un hogar activo da `404 device_not_found`.
- Las lecturas **no son un registro completo**: se guardan submuestreadas (como máximo una cada 5 minutos por dispositivo) y pueden perderse si el backend está saturado (`tracking.samples.dropped`). Hoy no vencen: la retención está sin decidir (D-TR-6).
- Los técnicos y administradores leen **cualquier** dispositivo, sin acotarlo al negocio al que pertenecen: el resto del backend tampoco acota por negocio a estos roles.
- No incluye los eventos `DEVICE_FAILURE`: están en el historial de eventos del dispositivo (D-TR-9 no pidió mezclarlos).

### Evidencia de rendimiento (criterio de TS-47: respuesta en menos de 200 ms)

`TelemetryQueryPerformanceOnMongoTest` siembra 100 000 lecturas de 1 000 dispositivos en un MongoDB real (contenedor) y mide 500 consultas (primera página y una página profunda). Medición del 2026-11-24 en la máquina de desarrollo: **p50 2,8 ms, p95 4,5 ms, máx. 6,2 ms**; la consulta usa el índice `household_device_time` sin ordenar en memoria y examina solo la página que devuelve (`explain`).

**Lo que esto no demuestra:** es un MongoDB local con caché caliente. No mide la latencia de red hacia el clúster Atlas M0 de producción ni sus límites de uso. **Verificación manual pendiente** en el entorno real: ejecutar la misma consulta contra el clúster (por ejemplo con `mongosh` y `explain("executionStats")`, o llamando al endpoint desplegado con volumen sembrado) y registrar el tiempo. Un volumen representativo de producción (cientos de miles de lecturas por mes) también está por confirmar.

---

## Resumen de autonomía para `analytics` (interno, no es una ruta HTTP)

`analytics` (AP-439/442/443, US-41/TS-33) no lee MongoDB: pide a Tracking por su fachada `TrackingContextFacade.summarizeAutonomy(householdId, deviceId, from, to)` (`com.resolum.alivia.tracking.interfaces.acl`). La respuesta usa solo primitivos y tipos de Java. **La fachada no decide quién puede ver el hogar:** `analytics` lo comprueba antes (por ejemplo con `AssistanceContextFacade.canReadAssistedPerson` y `fetchHouseholdIdOfAssistedPerson`), para devolver el `403` que pide el criterio «cuidador no vinculado».

### Qué se cuenta

Se cuentan **comandos**, no eventos: cada `commandId` cuenta una vez, aunque tenga una solicitud, un resultado, un reenvío o un resultado emitido de nuevo con otro `eventId`. Cada comando cae en **un solo** resultado, con esta precedencia:

1. **Resultado del actuador** (`SUCCESS`, `FAILED`, `FAILED_TIMEOUT`): el más reciente; a igual instante gana `SUCCESS`. Un resultado real que llega tarde gana a un `NO_CONFIRMATION` o a un timeout anterior.
2. **Rechazo** (`REJECTED_LOW_CONFIDENCE`, `REJECTED_UNAUTHORIZED`).
3. **`NO_CONFIRMATION`**: el edge de voz se rindió sin respuesta del actuador.
4. **En curso** (`inProgress`): se pidió y aún no hay nada más.

| Campo | Significado |
|---|---|
| `total.requested` | Comandos pedidos; es la suma de las seis categorías siguientes |
| `succeeded`, `failed` | El actuador lo hizo / falló o no terminó a tiempo (`failed` incluye `FAILED_TIMEOUT`) |
| `rejectedLowConfidence` | La voz no se reconoció con suficiente confianza y no se ejecutó (las «cuatro categorías» de US-41 #2 son éxito, falla, este y `noConfirmation`) |
| `rejectedUnauthorized` | No se ejecutó porque la persona no está autorizada. **Quinta categoría, aparte** de las cuatro de la historia, para no esconderla |
| `noConfirmation`, `inProgress` | Ver arriba |
| `executedLocally` | De los comandos que el actuador **ejecutó** (`succeeded` o `failed`), los que se hicieron sin conexión y llegaron después (`origin=SYNCED`). Se cuentan una sola vez y no son una categoría aparte |
| `byDevice[]` | Lo mismo por dispositivo (con comandos), por `deviceId` |
| `withoutDevice` | Comandos que no nombran dispositivo (un rechazo puede ocurrir antes de saber cuál) |
| `edgeLink`, `lastUpdatedAt`, `mayHavePendingEvents` | Como en el estado de los dispositivos: `lastUpdatedAt` es el último reporte de cualquier edge del hogar (la «fecha de la última actualización»), y `mayHavePendingEvents` avisa de que **las cuentas pueden seguir creciendo** |

**Sin audio ni transcripciones (RN-10):** el repositorio lee de cada evento solo `commandId`, tipo, resultado, origen, momento y dispositivo; no el detalle, ni los atributos, ni la confianza. Una prueba comprueba que el tipo `CommandFact` no tiene más campos.

### Límites que `analytics` debe conocer

- **Período:** `from` se incluye y `to` se excluye. Los comandos se cuentan por los eventos que ocurrieron **dentro** del período: un comando pedido justo antes de `from` y resuelto dentro cuenta con su resultado conocido, no como solicitud del período. Solo importa en los bordes.
- **Filtro por dispositivo:** un rechazo que no nombra dispositivo no entra en un resumen limitado a un dispositivo.
- **«Solicitadas» depende del edge de voz.** Se cuenta cada `commandId` visto, emita o no el edge un `COMMAND_REQUESTED`; pero un comando del que el backend solo recibió el resultado (porque la solicitud no llegó) también cuenta como solicitado. Mientras el edge no emita las solicitudes, `inProgress` y `requested` pueden estar subestimados para comandos aún en curso.
- **Volumen:** el recorrido no carga los eventos en memoria (uno por comando en un mapa); un período largo cuesta tiempo, no espacio. El período máximo lo fija `analytics` (D-RM-5); Tracking no impone uno.
- **Falla de lectura:** si MongoDB no responde, la fachada lanza `TrackingStorageUnavailable` (una `RuntimeException`) y **nunca** responde un resumen de ceros. `analytics` puede tratarlo como fuente caída y devolver una respuesta parcial.
- **Argumentos inválidos** (hogar nulo, período ausente o al revés): `IllegalArgumentException`; un período vacío (`from == to`) es válido y da ceros.

---

## `GET /api/v1/assisted-persons/{personId}/events/recent`

Lo último que pasó en el **hogar** de una persona asistida, del más reciente al más antiguo (US-22 escenario 2: «acciones y alertas recientes»). A diferencia del historial de un dispositivo, mezcla todo el hogar e **incluye los eventos sin dispositivo**: un pedido de ayuda, una pérdida de conexión de un equipo. Ejemplo ejecutable: `docs/fixtures/tracking/q-recent-events.response.json`. Opcional y derivado: si la web ya resuelve US-22 #2 con otro contrato, se puede no usar.

**Quién puede llamarlo:** la red de cuidado de la persona o un miembro del hogar con lectura (igual que el estado de los dispositivos). `personId` es el id de la **persona asistida** (`AssistedPerson`), no el del perfil (D-TR-12, confirmada).

| Parámetro | Significado |
|---|---|
| `limit` | Cuántos eventos. Por defecto 20, máximo 100 (los mismos límites del historial, D-TR-9, confirmados) |

**Respuesta `200`:** `personId`, `limit` e `items[]`. Cada evento es como los del historial de un dispositivo (`eventId`, `type`, `severity`, `origin`, `occurredAt`, `receivedAt` y, si aplican, `result`, `commandId`, `action`, `confidence`, `batteryPct`, `detail`, `attributes`) **más `deviceId`** cuando el evento es de un dispositivo. Los campos sin valor se omiten. Un hogar sin eventos es una lista vacía. Un comando aparece con varios eventos (su solicitud y su resultado): la lista es de eventos, no de comandos.

**Errores:** `400 invalid_limit` (fuera de 1 a 100) o `invalid_request` (no numérico); `401`; `403 access_denied` (la cuenta no es de un usuario); `404 assisted_person_not_found` (no existe o no puede leerla) o `assisted_person_without_household`; `503 tracking_storage_unavailable` con `Retry-After: 5`. El límite se valida antes de consultar nada.

**Orden:** por `occurredAt` y, a igual instante, por identificador interno, igual que el historial.
