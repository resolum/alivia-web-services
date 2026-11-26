# MongoDB en producción para Tracking

> **Estado:** 2026-11-24, paso T-6.1 del plan de Tracking. Lo que está **medido** se dice así y con su fuente; lo que es **estimación** o **supuesto** también. No se ejecutó nada contra Atlas ni contra Azure.
> **Quién decide qué:** el despliegue, Terraform, la elección del clúster (M0 o de pago) y la región son de la persona asignada a infraestructura. Este documento le da los datos; no cambia nada de ello.

## 1. Qué cambió en el código

| Cambio | Dónde | Por qué |
|---|---|---|
| `MANAGEMENT_HEALTH_MONGODB_ENABLED=true` | `infrastructure/scripts/deploy.sh` (y su prueba `deploy_test.sh`) | P-7 se revierte: Tracking lee y escribe en MongoDB, así que su caída ya no es «irrelevante» para la salud |
| Tiempos de espera del controlador | `MongoClientTimeoutsConfiguration` (`alivia.mongo.*`) | El controlador esperaba **30 s** a encontrar un servidor; más de lo que espera cualquiera de los que llaman |
| Contadores de Micrometer | `tracking.events`, `tracking.device.states`, `tracking.samples.dropped`, `tracking.storage.failures`, `tracking.link.*` | Ver §4 |

**Por qué `true` y no «quitar la variable»:** `az webapp config appsettings set` solo agrega o cambia valores, no borra. Un App Service que ya tiene `false` lo conservaría. Con `true` explícito, el siguiente despliegue lo corrige. La prueba `deploy_test.sh` comprueba ese valor.

## 2. La salud del despliegue y de App Service

Con el indicador de MongoDB activo, `/actuator/health` es `DOWN` cuando Atlas no responde. Hay dos consumidores:

1. **La verificación de `deploy.sh`** (`wait_for_health`): un Atlas caído durante un despliegue lo da por fallido y revierte la imagen. Es la consecuencia que P-7 aceptaba al revertirse.
2. **La comprobación de salud de App Service** (`health_check_path = /actuator/health` en `infrastructure/terraform/modules/platform/main.tf`, `health_check_eviction_time_in_min = 5`). Es **la que más importa**: con Atlas caído, App Service marcaría la instancia como no saludable aunque PostgreSQL y casi toda la API sigan funcionando. Con más de una instancia, la sacaría del balanceo. **Esto convierte una falla parcial (Tracking) en una indisponibilidad total.**

**Recomendación (no aplicada, es de Terraform):** que la comprobación de App Service apunte a un indicador de *vida* que no incluya MongoDB, y que `/actuator/health` completo quede para el despliegue y la observación. Spring Boot ofrece `/actuator/health/liveness`, pero solo si se habilita `management.endpoint.health.probes.enabled=true`, que **no** está en `application.properties`. Son dos cambios pequeños (una propiedad y `health_check_path`), pero cambian el comportamiento de producción; decide la persona asignada. Si se prefiere mantener todo como está, la consecuencia es la descrita.

**Tiempo de respuesta del indicador:** el indicador de MongoDB usa el tiempo de selección de servidor. Antes eran 30 s (medido el 2026-11-06 con la imagen real: `/actuator/health` tardó 30 s y respondió `DOWN`, ver `azure-deployment.md` §4.4); ahora 3 s. Una prueba (`MongoClientTimeoutsConfigurationTest`) comprueba con el controlador real y ningún servidor que se rinde cuando termina la espera configurada.

## 3. Tiempos de espera

| Propiedad (variable de entorno) | Por defecto | Qué acota |
|---|---|---|
| `alivia.mongo.server-selection-timeout` (`ALIVIA_MONGO_SERVER_SELECTION_TIMEOUT`) | `PT3S` | Cuánto se espera a encontrar un servidor. Es lo que determina la rapidez con que un Atlas caído se convierte en `503` |
| `alivia.mongo.connect-timeout` | `PT3S` | Abrir una conexión |
| `alivia.mongo.read-timeout` | `PT5S` | Esperar la respuesta de una operación |

Sustituyen a lo que traiga la cadena de conexión (`MONGODB_URI`), para que haya un solo lugar donde leerlos. **Los valores por defecto son una propuesta**, no una medición desde Azure: hay que contrastarlos con lo que tarda Atlas desde Brazil South (ver §6) y subirlos si la conexión TLS inicial tarda más de 3 s. Un valor de cero o negativo se rechaza al arrancar.

> `deploy.sh` y `cd-deploy.yml` **no** reenvían estas variables: hoy se usan los valores por defecto. Para cambiarlas sin tocar código hay que añadirlas a `OPTIONAL_APP_VARS` y al paso de entorno del flujo, o fijarlas a mano en App Service (lo que va contra P-4, el script es el único dueño de la configuración).

## 4. Métricas

| Medidor | Etiquetas | Qué cuenta |
|---|---|---|
| `tracking.events` | `outcome` = `accepted` / `duplicate` / `rejected`; `via` = `single` (E1) / `batch` (E2) | Qué pasó con cada evento que reportó un edge. Un `duplicate` alto es normal tras un corte; un `rejected` alto indica un edge mal configurado |
| `tracking.device.states` | `outcome` = `applied` / `stale` / `rejected` | Qué se decidió de cada reporte de dispositivo. `rejected` es «la anomalía registrada» de TS-55 #3 |
| `tracking.samples.dropped` | `cause` = `queue_full` / `write_failed` | Muestras de diagnóstico descartadas (se pueden perder) |
| `tracking.storage.failures` | `operation` | Lecturas o escrituras que fallaron: base de la alerta «MongoDB no responde» |
| `tracking.link.transitions`, `tracking.link.failures` | `to`; `stage` | Edges marcados `OFFLINE`; fallos del vigilante |

Se usó **una métrica con etiquetas** (`tracking.events{outcome=…}`) y no tres con sufijo (`tracking.events.accepted`…, como decía el plan): es la forma que Micrometer recomienda y permite sumar y filtrar.

**Aviso importante: hoy nadie puede ver estas métricas.** `management.endpoints.web.exposure.include` es `health,info`, y no hay ningún *registry* de exportación configurado (ni Application Insights ni Prometheus). Los contadores existen en memoria y se pierden al reiniciar. Hacerlas visibles requiere una decisión de operación (exponer `/actuator/metrics` autenticado, o añadir un exportador); no la tomé.

## 5. Retención de las muestras (D-TR-6, decidida el 2026-11-25)

**Las muestras de diagnóstico vencen a los 7 días** (`alivia.tracking.samples-retention`, por defecto `P7D`; `none` o vacío las conserva siempre). **Los eventos no vencen** por ahora. Es la opción recomendada del plan y la que elegiste; la razón es la capacidad del clúster gratuito (§6).

- **Qué hace:** cada muestra se guarda con `expiresAt = momento en que el backend la guardó + 7 días`, y el índice TTL (`expires`) la borra. MongoDB revisa los vencimientos más o menos cada minuto, así que el borrado no es exacto.
- **Qué no hace:** solo afecta a las muestras **que se guarden desde ahora**. Las que ya estuvieran guardadas sin fecha (no debería haber ninguna en producción: Tracking aún no tiene hogares reales) no vencerán; habría que ponerles `expiresAt` con un script.
- **El texto de los pedidos de ayuda (`detail`) es un dato personal y sigue guardándose sin vencer.** Se recorta a 200 caracteres. La política de privacidad de ese texto (¿vencer a los 12 meses?, ¿anonimizar?) **no está decidida**: es lo que queda de D-TR-6.
- **Para cambiarlo sin tocar código:** `ALIVIA_TRACKING_SAMPLES_RETENTION` (p. ej. `P30D`), pero `deploy.sh` y `cd-deploy.yml` no reenvían esa variable (ver §3): hoy rige el valor por defecto.

## 6. Dimensionamiento: M0 frente a un plan de pago, y la región

### Lo medido (2026-11-24, MongoDB en contenedor, pruebas `TelemetryQueryPerformanceOnMongoTest` y `DeviceEventsOnMongoTest`)

| Dato | Valor medido |
|---|---|
| Tamaño de un documento de muestra (`telemetry_samples`) | **134 bytes** (eran 241 con el campo `_class`) |
| Tamaño de un documento de evento (`device_events`) | **287 bytes** (eran 390 con `_class`; los de comando, algo más) |
| Campo `_class` | **Ya no se escribe** (`MongoTypeMappingConfiguration`): ahorra un 44 % en las muestras y un 26 % en los eventos. Los documentos escritos antes lo conservan y se leen igual |
| Consulta de telemetría con 100 000 lecturas, p95 | 3 a 5 ms **en local** |

No pude medir el tamaño de los **índices**: tras una inserción masiva MongoDB informa el tamaño de los índices desde el último punto de control, que aún no ocurrió (devolvió 12 KB para 100 000 lecturas). Por eso lo que sigue es **estimación**: ~35 B (índice de `_id`) + ~50 B (índice compuesto) + ~15 B (índice de expiración) por muestra, es decir **≈ 235 B por muestra** con sus índices, y ≈ 440 B por evento (287 B más cuatro índices).

### Lo que sale de esos números **[Derivado, con supuestos]**

Supuestos: un edge reporta cada 30 s; cada reporte trae diagnósticos de cada dispositivo; la muestra se guarda a lo sumo cada 5 min por dispositivo (`alivia.tracking.sample-interval`); un hogar tiene 10 dispositivos repartidos entre 2 edges; la capacidad de M0 es 512 MB (**verificar contra la documentación vigente de Atlas: no la consulté en esta sesión**).

| Escenario | Resultado |
|---|---|
| **Sin retención** (`none`; ya no es lo que rige), 1 muestra / 5 min | 8 640 muestras por dispositivo y mes ≈ **2,0 MB**; por hogar de 10 dispositivos ≈ **20 MB al mes**. M0 se llena con las muestras de unos **25 hogares-mes** |
| **Con retención de 7 días (lo que rige desde 2026-11-25)** | ≈ 4,7 MB por hogar en régimen estable: unos **108 hogares** solo de muestras |
| Con muestras cada 30 min y sin retención | ≈ 3,4 MB por hogar y mes: unos 150 hogares-mes |
| Eventos | Depende del uso; con 100 eventos al día por hogar (**supuesto sin respaldo**) ≈ 1,3 MB por hogar y mes: no es lo que llena el clúster |

**Conclusión:** sin retención ni muestreo más espaciado, el clúster gratuito se llena en cuestión de meses con pocas decenas de hogares (antes de quitar `_class`, en semanas). Fue la razón principal para decidir D-TR-6, ya resuelta con 7 días.

**Operaciones por segundo (límite de M0, **a verificar**).** Con 10 dispositivos repartidos en 2 edges, cada 30 s se hacen ≈ 16 operaciones por hogar: una lectura de estados por reporte (2), una escritura por dispositivo (10) y dos operaciones de latido por reporte (4), es decir ≈ **0,5 operaciones por segundo por hogar**. Si M0 limita a unas 100 operaciones por segundo, el techo está en unos **190 hogares**, sin contar eventos ni muestras (que añaden poco). *Corrige una cuenta anterior de este documento que contaba los dispositivos dos veces (≈ 1,5 por segundo y 60 hogares) y que, además, leía el estado de cada dispositivo por separado: ahora se lee una vez por reporte (T-6.1 bis); antes eran ≈ 24 operaciones por hogar cada 30 s, ≈ 0,8 por segundo y unos 125 hogares.*

### La región (D-11, R-12) **[Derivado, sin medir]**

La API está en Brazil South y Atlas M0 en East US. Antes de Tracking eso «no costaba nada» (`azure-deployment.md` §A.4). Ahora sí: el reporte de estados hace operaciones **en serie**: con 10 dispositivos en un solo edge, ≈ 13 (una lectura de todos los estados, una escritura por dispositivo y las dos del latido; eran ≈ 22 cuando se leía de uno en uno). Con ~100 a 150 ms de ida y vuelta entre regiones (estimación, no medida), un reporte tardaría del orden de **1,3 a 2 s**, contra los 10 s que espera el edge: cabe, y se suma a cualquier lentitud de Atlas. Un lote de eventos (E2) ya no hace una operación por evento: se guarda en **una sola escritura**, así que cuesta un viaje y no hasta cien (corregido tras la revisión integral, R-04). Las consultas de lectura (una o dos operaciones) pagan ~100 a 300 ms, lo que **pone en duda el criterio de 200 ms de AP-485 en producción** aunque en local mida 5 ms.

Caminos (decide la persona asignada):

1. **Un clúster de pago en Brazil South** (Atlas lo ofrece en todas las regiones, R-12): elimina el salto entre regiones y el techo de M0.
2. **Reducir aún más las operaciones en serie**: las escrituras de estado se podrían enviar en una sola operación en bloque (`bulkWrite`); no se hizo porque cada una necesita su condición «a menos que haya uno más nuevo» y su resultado individual, y la lectura única ya quitó lo más caro.
3. Mantener M0 en East US: aceptable solo para demostración o con pocos hogares.

### Verificación manual pendiente

Contra el clúster real (staging), con la persona asignada: (a) medir el tiempo de `db.runCommand({ping: 1})` desde un contenedor en Brazil South, (b) ejecutar la consulta de telemetría con `explain("executionStats")` y registrar `executionTimeMillis`, (c) confirmar los límites vigentes de M0, (d) con `/actuator/health` y Atlas detenido, medir cuánto tarda en responder `DOWN` (debería rondar los 3 s). Ninguna de estas se hizo.

## 7. Qué queda a la persona asignada

- Si Atlas ya tiene el índice `household_device_time` de `device_events` (de una versión anterior de Tracking), se puede borrar: el historial usa ahora `household_device_time_id`, que añade `_id` para que dos eventos del mismo instante no cambien de lugar entre páginas. Se crea solo al arrancar (`auto-index-creation=true`); el nombre cambió a propósito, porque un índice con el mismo nombre y otras claves haría fallar el arranque.
- Decidir M0 o plan de pago, y la región (§6).
- Decidir si la comprobación de salud de App Service apunta a un indicador que no incluya MongoDB (§2).
- Reenviar las variables `ALIVIA_MONGO_*` y `ALIVIA_TRACKING_SAMPLES_RETENTION` desde el despliegue cuando se decidan (§3, §5).
- Decidir cómo se ven las métricas (§4).
- Actualizar el estado de Terraform de `staging`, que todavía registra `MANAGEMENT_HEALTH_MONGODB_ENABLED=false`; el siguiente despliegue con el `deploy.sh` nuevo lo deja en `true`.
