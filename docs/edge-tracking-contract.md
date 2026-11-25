# Contrato Edge ↔ backend: eventos, estados y latido (Tracking)

> **Estado:** propuesta v0.1 del 2026-11-24; las decisiones de backend D-TR-2, 3, 5 y 10 están resueltas (§7) y **falta la aceptación** de los responsables de `resolum/edge-devices` (edge de voz) y `resolum/edge-actuator` (edge de actuadores). Nada de lo marcado «nuevo» existe todavía en el backend.
> **Origen:** `opencode/plans/alivia-web-services-tracking.md` §5.3 (T-0.1). Los ejemplos están en `docs/fixtures/tracking/*.json`; el backend los reutilizará como pruebas de contrato (T-2.2 a T-2.4) y los Edge pueden usarlos en las suyas.
> **Qué existe hoy:** `POST /api/v1/edge/events` (E1), `GET /api/v1/edge/device-table` y `PUT /api/v1/edge/sync-state`, con la credencial por edge. Ver `opencode/reviews/alivia-web-services/AP-68-edge-transport.md`.
> **Regla de cambios:** una vez que un Edge adopte el contrato solo se permiten cambios **aditivos** (campos opcionales nuevos). Cualquier otro cambio exige coordinar los tres repositorios, como ocurrió con el `ETag` de AP-68.

## 1. Principios

1. **El acuse es durable.** El backend responde `ACCEPTED` o `DUPLICATE` solo **después** de guardar el evento. El Edge puede borrar de su cola únicamente lo confirmado.
2. **La idempotencia es del servidor.** Un evento se identifica por `(edge autenticado, eventId)`. Reenviarlo —por el mismo endpoint o por otro— devuelve `DUPLICATE` y no repite efectos (ni notificaciones).
3. **El servidor decide la severidad y el origen** (`LIVE` o `SYNCED`); el Edge no los envía.
4. **Un Edge solo informa sobre su propia instalación.** El edge sale de la credencial, nunca del cuerpo; cada `deviceId` se valida contra la tabla vigente de esa instalación (la que el Edge ya descarga).
5. **Nunca audio ni transcripciones.** El único texto libre permitido es `detail` (≤ 200 caracteres), con las palabras del pedido de ayuda o la causa de una falla, como hoy.
6. **Un pedido de ayuda no depende del almacenamiento.** Si guardar falla, el backend notifica igualmente a los familiares y responde `202`; el fallo se registra.

## 2. Autenticación, errores y reintentos

- **Autenticación:** `Authorization: Bearer <edgeId>.<secreto>` (la credencial de AP-68). Roles `EDGE_MICROPHONE` o `EDGE_ACTUATOR`; un usuario con token recibe `403`.
- **Errores:** `ProblemDetail` con `code` y `errors[]`, igual que el resto de la API.

| Código | Cuándo | Qué hace el Edge |
|---|---|---|
| `200` / `202` | Procesado (ver cada endpoint) | Borra de la cola **solo** lo confirmado |
| `400` | Cuerpo ilegible o lote demasiado grande (`batch_too_large`) | No reintentar igual: corregir o partir el lote |
| `401` | Credencial inválida o revocada | Conservar la cola; alarma local |
| `403` | Credencial de usuario, no de edge | No reintentar |
| `404` `edge_without_installation` | El edge no está asignado a una instalación activa | Conservar la cola y reintentar con espera larga |
| `429` / `503` | Demasiados intentos fallidos / fallo transitorio | Esperar `Retry-After` y reintentar con la **misma** cola y los **mismos** `eventId` |

- **Reintentos:** retroceso creciente con tope (como ya hacen ambos Edge), conservando el orden. El `eventId` de un evento **no cambia** entre reintentos.

## 3. Operaciones

### E1 — `POST /api/v1/edge/events` (existe)

Eventos **urgentes** con notificación inmediata a los familiares a cargo del hogar. Contrato actual sin cambios: `eventId`, `type`, `deviceId?`, `batteryPct?`, `detail?`; respuesta `202 {"notified": n}`. Tipos aceptados hoy: `HELP_REQUESTED`, `LOW_BATTERY`, `DEVICE_FAILURE`.

- **Novedad aditiva:** `occurredAt` opcional (ISO-8601 UTC), ya aceptado por el backend. Sin él se usa la hora de recepción; con él adelantado más de la tolerancia (5 min) también se usa la de recepción. Un Edge que no lo envía sigue funcionando igual.
- **Novedad de comportamiento (T-2.2):** además de notificar, el backend **guarda** el evento. Como es urgente, **nunca se rechaza** por un dispositivo desconocido (se guarda sin dispositivo), por un reloj adelantado ni por un `detail` largo (se corta); los familiares se notifican **antes** de guardar y aunque guardar falle. Ejemplo: `e1-event.request.json` / `e1-event.response.json`.

### E2 — `POST /api/v1/edge/events/batch` (nuevo; TS-54)

Lote **ordenado** de eventos del Edge, con resultado **por evento**. Sirve tanto en línea (lote de uno) como para sincronizar tras un corte. Ejemplos: `e2-batch.request.json` / `e2-batch.response.json`.

| Campo | Obligatorio | Descripción |
|---|---|---|
| `eventId` | sí | Estable por evento; patrón `[A-Za-z0-9._:-]{1,48}` (el mismo de E1). Un UUID sin guiones (32 hex) sirve |
| `type` | sí | Ver §4 |
| `occurredAt` | sí | Cuándo ocurrió, ISO-8601 UTC. Para eventos sincronizados es la hora **original**, no la del reenvío |
| `deviceId` | según el tipo | Dispositivo de la tabla de la instalación |
| `commandId` | en los eventos de comando | El `correlationId` del contrato MQTT v2; une la solicitud, el resultado y los rechazos |
| `action` | en los eventos de comando | `open`, `close`, `on` u `off` (los verbos del contrato MQTT v2) |
| `result` | en `COMMAND_RESULT` y `COMMAND_REJECTED` | Ver §4 |
| `confidence` | opcional | Confianza del modelo de voz, 0 a 1 |
| `batteryPct` | opcional | 0 a 100 |
| `detail` | opcional | ≤ 200 caracteres |
| `attributes` | opcional | Mapa de valores escalares para datos variables (p. ej. `state`, `elapsedMs`) |

- **Respuesta `200`:** `{"results": [{"eventId", "status", "reason?"}]}` en el mismo orden. `status`: `ACCEPTED`, `DUPLICATE` (ya estaba guardado: el Edge lo da por sincronizado) o `REJECTED` (con `reason`; reintentarlo no cambia nada: el Edge lo aparta con el motivo, como pide TS-54 #3).
- **Eventos urgentes dentro del lote** (`HELP_REQUESTED`, `DEVICE_FAILURE`, `LOW_BATTERY`): se tratan como en E1: se notifica a los familiares con la misma clave por `eventId` (reenviarlos o enviarlos antes por E1 no notifica dos veces), **nunca** se rechazan por un dispositivo desconocido, un reloj adelantado o un `detail` largo, y solo se ignoran en ellos `commandId`, `action`, `result`, `confidence` y `attributes`. Siguen rechazándose un `eventId` mal formado y una batería fuera de 0 a 100. **Un pedido de ayuda que llega con más de 5 minutos de retraso** (`alivia.tracking.help-delayed-after`, medido entre su `occurredAt` y cuando el backend lo recibe, por E1 o por el lote) se notifica como `HELP_REQUEST_DELAYED` y dice la hora real («Tu familiar pidió ayuda a las 07:12, pero el aviso llegó con retraso porque la casa estuvo sin conexión»; con fecha si fue otro día, en la hora de Lima, `alivia.tracking.zone`), para que la familia no lo tome por una emergencia que ocurre ahora. Si ese evento ya está guardado, **no se notifica otra vez**: el tipo cambia con el retraso y Comunicaciones no reconocería el aviso como el que ya tiene. Un pedido dentro del plazo notifica como `HELP_REQUESTED`. Solo se distingue el pedido de ayuda; `DEVICE_FAILURE` y `LOW_BATTERY` conservan su tipo. **Para el Edge:** conviene enviar siempre el `occurredAt` real del evento. (Decisión D-TR-8, 2026-11-25.)
- **Respuesta solo por lo guardado:** si el backend no puede guardar los eventos responde `503` con `Retry-After: 5` y código `tracking_storage_unavailable`; el Edge conserva el lote y lo reenvía idéntico (los ya guardados vuelven como `DUPLICATE`). Es la diferencia con E1, que responde `202` aunque no pueda guardar porque ya notificó.
- Un evento rechazado **no** impide los demás. `400` solo si el cuerpo completo es ilegible o supera el límite (`problem-batch-too-large.json`).

### E3 — `PUT /api/v1/edge/device-states` (nuevo; TS-55)

Estado de los dispositivos del Edge y **latido** del propio Edge. Con `devices` vacío es solo un latido (`e3-heartbeat-only.request.json`). Ejemplos: `e3-device-states.request.json` / `e3-device-states.response.json`.

| Campo | Descripción |
|---|---|
| `reportedAt` | Cuándo se armó el reporte |
| `edge.pendingEvents` | Cuántos eventos tiene el Edge sin sincronizar (permite avisar «pueden existir eventos pendientes») |
| `edge.uptimeS` | Opcional |
| `devices[].deviceId` | Dispositivo de la tabla |
| `devices[].connectionStatus` | `CONNECTED`, `DISCONNECTED` o `UNKNOWN` |
| `devices[].operationalStatus` | `OK`, `DEGRADED` o `FAILED` |
| `devices[].currentState` | `open`, `closed`, `on`, `off`…, según el tipo |
| `devices[].batteryPct` | 0 a 100, si el dispositivo la mide |
| `devices[].lastCommunicationAt` | Última comunicación del Edge con el dispositivo |
| `devices[].observedAt` | Cuándo se **observó** ese estado (permite descartar reportes atrasados) |
| `devices[].faults[]` | Códigos de falla; vacío si no hay |
| `devices[].diagnostics` | Mapa de métricas (rssi, memoria libre…); se guardan submuestreadas (como máximo una muestra por dispositivo cada `PT5M`) y pueden perderse si el backend está saturado; vencen solo cuando se configura la retención (D-TR-6, pendiente) |

- **Respuesta `200`:** `{"results": [{"deviceId", "status", "reason?"}]}`. `APPLIED`: el estado es ahora el último confiable. `STALE`: llegó un `observedAt` anterior al ya guardado (reporte atrasado tras un corte): **no** reemplaza al vigente; no es un error. `REJECTED`: dato inválido (con `reason`): se **conserva el último estado confiable** y se registra la anomalía (TS-55 #3).
- **Historial que produce un cambio de estado:** a partir del segundo reporte de un dispositivo, el servidor agrega al historial `DEVICE_DISCONNECTED` (pasó a desconectado), `DEVICE_FAILURE` (pasó a `FAILED`, con los códigos de `faults` como detalle), `DEVICE_RECOVERED` (volvió) y `DEVICE_STATE_CHANGED` (cambió `currentState`, con `state` y `previous`). Repetir el mismo estado cada 30 s no agrega nada. Cada evento lleva un id derivado del dispositivo y del `observedAt`, así que reenviar el reporte no lo duplica. Cuando el Edge vuelve de una caída se registra un `EDGE_RECONNECTED` (uno solo por caída). Estos eventos de dispositivo **no** notifican a la familia por sí solos (la batería baja y las fallas las avisa el propio Edge con E1/E2). Sí se notifica el enlace del edge: ver «Vigilancia del enlace».
- **Respuesta solo por lo guardado:** si no se puede guardar el estado, los eventos o el latido, `503` con `Retry-After: 5` y `tracking_storage_unavailable`; el Edge reenvía el mismo reporte. El servidor escribe primero los eventos derivados y después el estado, para que el reintento no pierda el cambio. Más de 100 dispositivos: `400 batch_too_large`.
- **Vigilancia del enlace:** el backend considera caído a un Edge que no reporta durante `PT3M` (`alivia.tracking.edge-offline-after`). Una tarea revisa cada `PT30S` (`alivia.tracking.link-check-interval`), registra `EDGE_DISCONNECTED` (id `dc-edge-<edgeId>-<último reporte en ms>`) y notifica `EDGE_DISCONNECTED` a los familiares principales («Casa sin conexión»), diciendo **qué equipo** calló: el de voz («sus avisos, como un pedido de ayuda, pueden llegar con retraso») o el de puertas, ventanas, cortinas y luces. El tipo de edge sale del inventario y se guarda con su estado. El primer reporte posterior es la reconexión: registra `EDGE_RECONNECTED` (una sola vez por caída) y notifica `EDGE_RECONNECTED` («Casa conectada de nuevo»). Tras reiniciar el backend espera `PT2M` (`alivia.tracking.link-startup-grace`) antes de juzgar a nadie, porque durante la caída del backend los Edge no pudieron reportar. **El Edge no necesita hacer nada especial al reconectar:** basta con que vuelva a llamar a E3 (y, si tiene pendientes, a E2).
- Toda llamada correcta actualiza el latido del Edge. **Cadencia propuesta:** cada 30 a 60 s con conexión, y siempre al reconectar. El backend considera caído a un Edge sin reporte durante `PT3M` (configurable).

## 4. Tipos, resultados y severidad

El Edge envía el tipo; **el servidor deriva la severidad** (RNF04: informativo, preventivo, importante, crítico).

| Tipo | Quién lo envía | Severidad (servidor) |
|---|---|---|
| `COMMAND_REQUESTED` | Edge de voz (tras confirmar la orden) | Informativo |
| `COMMAND_RESULT` | Edge de actuadores (resultado físico); el de voz envía `NO_CONFIRMATION` si vence la espera | Informativo si `SUCCESS`; importante en los demás resultados |
| `COMMAND_REJECTED` | Edge de voz | Informativo (`REJECTED_LOW_CONFIDENCE`); importante (`REJECTED_UNAUTHORIZED`) |
| `DEVICE_STATE_CHANGED`, `DEVICE_RECOVERED` | Cualquiera | Informativo |
| `LOW_BATTERY` | Cualquiera | Preventivo |
| `DEVICE_FAILURE` | Cualquiera | Importante |
| `HELP_REQUESTED` | Edge de voz (voz o botón) | Crítico |
| `EDGE_DISCONNECTED`, `DEVICE_DISCONNECTED` | **Solo el servidor** | Importante |
| `EDGE_RECONNECTED` | **Solo el servidor** | Informativo |

- `result` de `COMMAND_RESULT`: `SUCCESS`, `FAILED`, `FAILED_TIMEOUT`, `NO_CONFIRMATION`.
- `result` de `COMMAND_REJECTED`: `REJECTED_LOW_CONFIDENCE`, `REJECTED_UNAUTHORIZED`.
- Estos cuatro resultados y los dos rechazos son los que pide la métrica de autonomía (US-41 #2). Si llegan el `NO_CONFIRMATION` del Edge de voz **y** luego el resultado real del de actuadores para el mismo `commandId`, el del actuador prevalece al calcular métricas.
- **Origen (servidor):** `SYNCED` cuando el evento llega por E2 con retraso respecto de `occurredAt` (se muestra como «ejecutado localmente»); `LIVE` en los demás casos.

## 5. Validación y límites

Valores por defecto, **configurables** en el backend (`alivia.tracking.*`); el Edge no debe asumirlos fijos.

| Regla | Valor propuesto | `reason` al rechazar |
|---|---|---|
| Eventos por lote | 100 | `400 batch_too_large` |
| `eventId` | patrón de §3 | `invalid_event_id` |
| Tipo permitido a un Edge | los de §4 que no son «solo el servidor» | `type_not_allowed` |
| Dispositivo en la tabla de **esa** instalación | obligatorio si el tipo lo pide | `unknown_device` |
| `occurredAt` no posterior a ahora + tolerancia | 5 min | `occurred_in_future` |
| `detail` | ≤ 200 caracteres | `detail_too_long` |
| `attributes` | ≤ 20 claves escalares (nombres de 1 a 40 caracteres `A-Za-z0-9_-`, **sin puntos**: MongoDB no los admite) y ≤ 2 KB; claves prohibidas: `audio`, `pcm`, `wav`, `samples`, `transcript` | `attributes_invalid`, `forbidden_attribute` |
| `batteryPct` | 0 a 100 | `battery_out_of_range` |
| Enumeraciones | valores de §3 y §4 | `invalid_status` |
| Dispositivo obligatorio según el tipo (`COMMAND_REQUESTED`, `COMMAND_RESULT`, `DEVICE_STATE_CHANGED`, `DEVICE_RECOVERED`) | presente | `device_required` |
| `commandId` (patrón `[A-Za-z0-9._:-]{1,64}`) y `action` en los eventos de comando | obligatorios; `action` solo en `COMMAND_REQUESTED` y `COMMAND_RESULT` | `command_data_required`, `invalid_action` |
| `result` | obligatorio en `COMMAND_RESULT` y `COMMAND_REJECTED`, de su propio conjunto (§4); prohibido en los demás tipos | `invalid_result` |
| `confidence` | 0 a 1 | `confidence_out_of_range` |
| Fechas con formato inválido | ISO-8601 UTC | `invalid_timestamp` |

## 6. Obligaciones del Edge

1. Dar a **cada** evento un `eventId` estable y su `occurredAt` en UTC; **no** regenerarlos al reintentar.
2. **Persistir la cola en disco** y no descartar eventos por estar llena: hoy ambos Edge la guardan solo en memoria (200 eventos, descartando el más antiguo) y pierden lo pendiente al reiniciar. Sin esto no se cumple RNF07 (0 % de pérdida).
3. Enviar en el **orden** de ocurrencia; marcar como sincronizado solo lo que el backend confirmó (`ACCEPTED` o `DUPLICATE`).
4. Informar **cada** orden, también las exitosas: el Edge de actuadores hoy solo informa fallas.
5. Respetar `Retry-After` en `429` y `503`.
6. Enviar la batería en **porcentaje** (`batteryPct`). Hoy la telemetría del ESP32 de actuadores trae `batteryMv` y el reportero solo evalúa `batteryPct`: acordar con el firmware quién convierte.
7. No enviar audio ni transcripciones.

## 7. Decisiones pendientes (resumen)

Detalle y recomendaciones en `opencode/plans/alivia-web-services-tracking.md` §11.1. Las que bloquean la implementación de la ingesta:

| ID | Decisión | Estado |
|---|---|---|
| D-TR-2 | Mover `POST /edge/events` de `assets` a `tracking` conservando su contrato | **Resuelta 2026-11-24: mover** |
| D-TR-3 | Acuse tras escritura durable; asincronía solo para muestras | **Resuelta 2026-11-24: acuse tras escribir** |
| D-TR-4 | Rutas y esquemas de E2/E3, tipos y resultados | **Este documento**: pendiente de aceptación de los dos Edge |
| D-TR-5 | Umbrales (lote 100, tolerancia 5 min, `PT3M`, latido 30–60 s, `attributes` 2 KB) | **Resuelta 2026-11-24: aceptados** como valores por defecto configurables |
| D-TR-10 | Dependencia de pruebas `testcontainers-mongodb` y versión fija de Mongo en `compose.yaml` | **Resuelta 2026-11-24: autorizada** |

## 8. Fixtures

| Archivo | Contenido |
|---|---|
| `e1-event.request.json`, `e1-event.response.json` | Pedido de ayuda con `occurredAt` |
| `e2-batch.request.json`, `e2-batch.response.json` | Lote mixto: solicitud, resultado, rechazo, cambio de estado, batería baja y un dispositivo desconocido; la respuesta incluye `ACCEPTED`, `DUPLICATE` y `REJECTED` |
| `e3-device-states.request.json`, `e3-device-states.response.json` | Cuatro dispositivos: `APPLIED`, `STALE`, `REJECTED` por batería y `REJECTED` por dispositivo desconocido |
| `e3-heartbeat-only.request.json` | Latido sin dispositivos |
| `problem-batch-too-large.json` | `400` del lote (código propuesto) |
| `problem-edge-without-installation.json` | `404` existente (`edge_without_installation`) |
