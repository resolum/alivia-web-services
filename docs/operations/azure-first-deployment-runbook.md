# Runbook: primer despliegue de un entorno en Azure

> Guía operativa paso a paso para dejar `staging` y `production` desplegando desde GitHub Actions. El **porqué** de cada
> decisión está en [`azure-deployment.md`](azure-deployment.md); aquí solo está el **cómo**. Estado: redactado el
> 2026-11-06 (paso 6, AP-466). **Los comandos de `gh` y los de GitHub no se han ejecutado todavía** (en la máquina donde se
> redactó no está instalado `gh`): se marcan **[Sin probar]**. Si un comando falla, se corrige aquí.
>
> Todos los comandos con barras y `$(...)` están pensados para **Git Bash**, no para PowerShell: PowerShell añade un salto
> de línea al canalizar texto a un programa y un secreto o una URL con ese salto de línea deja de ser válido.

## 0. Qué hace falta

| Cosa | Cómo comprobarlo |
|---|---|
| Terraform aplicado en el entorno (`shared` y `staging`/`production`), con **`plan` sin cambios** | `terraform plan` en la carpeta del entorno dice *No changes* |
| La identidad de despliegue del entorno creada (módulo `deploy-identity`) | `terraform output deploy_identity_client_id` devuelve un UUID |
| `Microsoft.ManagedIdentity` registrado | `az provider show -n Microsoft.ManagedIdentity --query registrationState -o tsv` → `Registered` |
| GitHub CLI y sesión con permisos de administrador del repositorio | `gh auth status` |
| Los valores de terceros a mano: claves de Stripe (de prueba en staging), credenciales de Cloudinary, clave de Resend, URL del frontend | — |

Instalar `gh` (una vez):

```bash
winget install GitHub.cli        # reabre la terminal después
gh auth login                    # GitHub.com, HTTPS, navegador
gh auth status
```

Variables que se usan en todos los bloques de abajo (cámbialas por entorno):

```bash
export ENV=staging                                    # staging | production
export R=resolum/alivia-web-services
export TF_DIR=infrastructure/terraform/environments/$ENV
```

## 1. Los entornos de GitHub

Un **entorno** de GitHub agrupa variables, secretos y reglas de protección, y es lo que Azure comprueba al validar el
inicio de sesión (el nombre forma parte del sujeto OIDC `repo:resolum/alivia-web-services:environment:<nombre>`). Por eso:

- Los nombres tienen que ser **exactamente** `staging` y `production`, en minúsculas.
- **Hay que crearlos antes de la primera ejecución del workflow.** Si no existen, GitHub los crea solo y **sin ninguna
  protección**: el primer despliegue a `production` saldría sin aprobación (R-16).
- Ni variables ni secretos van a nivel de repositorio: van **en el entorno**, porque cada entorno tiene valores distintos.

### 1.1 Por la interfaz web

*Repositorio → Settings → Environments → New environment.*

| Ajuste | `staging` | `production` |
|---|---|---|
| Nombre | `staging` | `production` |
| Required reviewers | no | **sí**: añade a quien aprueba los despliegues. Con «Prevent self-review» activado, quien lanzó el despliegue no puede aprobarlo; si el equipo es de una persona, déjalo desactivado |
| Wait timer | no | no |
| Deployment branches and tags | sin restricción (permite probar desde ramas) | **Selected branches and tags** → añadir la regla `main` |
| Allow administrators to bypass | por defecto | **desactivar**, si la opción aparece |

Después, en cada entorno, *Environment secrets* y *Environment variables* se rellenan en la sección 3.

### 1.2 Por la línea de comandos **[Sin probar]**

```bash
# staging: sin protecciones
gh api -X PUT "repos/$R/environments/staging"

# production: revisores obligatorios y solo la rama main
REVIEWER_LOGIN="tu-usuario-de-github"
REVIEWER_ID=$(gh api "users/$REVIEWER_LOGIN" --jq .id)           # para más de un revisor, añade más entradas a "reviewers"
gh api -X PUT "repos/$R/environments/production" --input - <<JSON
{
  "reviewers": [ { "type": "User", "id": $REVIEWER_ID } ],
  "deployment_branch_policy": { "protected_branches": false, "custom_branch_policies": true }
}
JSON
gh api -X POST "repos/$R/environments/production/deployment-branch-policies" -f name=main -f type=branch
```

### 1.3 Comprobar las protecciones de `production`

```bash
gh api "repos/$R/environments/production" --jq '{protection_rules, deployment_branch_policy}'
```

Debe verse una regla `required_reviewers` con tu usuario y `custom_branch_policies: true`. La comprobación **definitiva**
es probar: lanza un despliegue a `production` desde otra rama (sección 6.3) y comprueba que GitHub lo rechaza.

## 2. Qué valor va en cada variable

Son **22 elementos obligatorios** por entorno (8 secretos y 14 variables) y 18 opcionales. Un valor vacío cuenta como no
definido: `deploy.sh` se detiene y lista los **nombres** que faltan, nunca los valores.

| Nombre | Tipo | De dónde sale | Notas |
|---|---|---|---|
| `AZURE_CLIENT_ID` | variable | `terraform output -raw deploy_identity_client_id` | **De ese entorno**: la identidad de staging no vale en production |
| `AZURE_TENANT_ID` | variable | `terraform output -raw azure_tenant_id` | |
| `AZURE_SUBSCRIPTION_ID` | variable | `terraform output -raw azure_subscription_id` | |
| `AZURE_RESOURCE_GROUP` | variable | `terraform output -raw resource_group_name` | |
| `AZURE_WEBAPP_NAME` | variable | `terraform output -raw webapp_name` | Debe empezar por `app-alivia-<entorno>-`; `deploy.sh` lo comprueba |
| `ACR_NAME` | variable | `terraform output -raw acr_name` | El mismo ACR para los dos entornos |
| `DB_URL` | variable | `terraform output -raw postgres_jdbc_url` | Ya trae `sslmode=require` |
| `DB_USERNAME` | variable | `terraform output -raw postgres_administrator_login` | |
| `MONGODB_DATABASE` | variable | `terraform output -raw mongodb_database` | |
| `DB_PASSWORD` | **secreto** | `terraform output -raw postgres_administrator_password` | |
| `MONGODB_URI` | **secreto** | `terraform output -raw mongodb_connection_string` | |
| `IAM_JWT_SECRET` | **secreto** | Se genera (sección 3.2) | Mínimo 32 bytes. Rotarlo cierra todas las sesiones |
| `IAM_SUPER_ADMIN_PASSWORD` | **secreto** | Se genera o se elige | **Guárdala en un gestor de contraseñas**: con ella se entra como administrador y GitHub no permite leerla después |
| `IAM_SUPER_ADMIN_EMAIL` | variable | Lo eliges | No puede ser el marcador `EMAIL` |
| `STRIPE_SECRET_KEY` | **secreto** | Stripe → Developers → API keys | `sk_test_...` en staging; `sk_live_...` en production |
| `STRIPE_WEBHOOK_SECRET` | **secreto** | Stripe → Developers → Webhooks, al registrar el endpoint (sección 5.1) | `whsec_...`. Es distinto en cada entorno |
| `STRIPE_PUBLISHABLE_KEY` | variable | Stripe → API keys | `pk_test_...` / `pk_live_...`. Pública por diseño |
| `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | **secretos** | Panel de Cloudinary | |
| `CLOUDINARY_CLOUD_NAME` | variable | Panel de Cloudinary | |
| `CORS_ALLOWED_ORIGINS` | variable | URL(s) del frontend de ese entorno | Separadas por coma, con `https://`, **sin** barra final |
| `IAM_EMAIL_VERIFICATION_LINK_BASE_URL` | variable | `<url del frontend>/verify-email` | Revisa la ruta real del frontend |

Opcionales que **sí conviene definir** (sin ellas la aplicación usa un valor pensado para desarrollo; `deploy.sh` avisa):

| Nombre | Tipo | Valor recomendado |
|---|---|---|
| `IAM_EMAIL_PROVIDER` | variable | `resend`. El valor por defecto en `prod` es `unavailable`: no sale ningún correo y **el registro y las invitaciones de usuarios fallan**. `deploy.sh` rechaza `smtp` (no copia `spring.mail.*`) y `log` (la aplicación no arrancaría). Ver 2.1 |
| `RESEND_API_KEY` | **secreto** | Clave de Resend. `deploy.sh` la **exige** si el proveedor es `resend` |
| `IAM_EMAIL_FROM` | variable | Solo la dirección (`no-reply@mail.<dominio>`), de un dominio **verificado en Resend**. `deploy.sh` la **exige** con `resend` y rechaza una dirección con nombre visible |
| `IAM_EMAIL_FROM_NAME` | variable | Nombre visible del remitente (por defecto `Alivia`) |
| `IAM_ACCOUNT_ACTIVATION_LINK_BASE_URL` | variable | `<url del frontend>/activate-account`. Su valor por defecto es `http://localhost:4200/activate-account` |

Los demás opcionales (`HCM_DEFAULT_BUSINESS_ID`, `IAM_DEFAULT_PHOTO_URL`, tiempos de los tokens, `JAVA_TOOL_OPTIONS`,
etc.) están en `OPTIONAL_APP_VARS` de `deploy.sh`; se definen solo si hay que cambiar el valor por defecto.

### 2.1 Correo con Resend (hacerlo antes de cargar los valores)

**Por qué importa.** La API no envía correo ella misma: se lo entrega a Resend. Si el envío falla, **falla también el
alta**: el registro de usuarios y la invitación de personal devuelven error (`RegistrationUnavailableException`), y con
`IAM_EMAIL_PROVIDER` sin definir (en `prod` es `unavailable`) falla siempre. Lo único que se envía hoy son el correo de
verificación de cuenta y la invitación de personal.

**Reglas de Resend** (de su documentación, consultada el 2026-11-07):

| Regla | Consecuencia |
|---|---|
| Se envía desde un **dominio verificado** por DNS | Con un dominio sin verificar: HTTP 403 `The <dominio> domain is not verified` |
| Sin dominio propio solo se puede enviar a **la dirección de la propia cuenta** de Resend | HTTP 403 `You can only send testing emails to your own email address`: los usuarios reales no reciben nada |
| Plan gratuito: **100 correos al día** y 3.000 al mes, 3 dominios | Cada registro, reenvío e invitación cuenta; al llegar al límite, el alta falla |
| Se recomienda enviar desde un **subdominio** (por ejemplo `mail.<dominio>`) | Aísla la reputación de envío del dominio principal |

**El dominio.** El equipo dispone de un dominio gratuito de GitHub Education. Esas ofertas son **de duración limitada**
(en la página del paquete: Namecheap `.me` y `.TECH`, un año; Name.com, sin duración indicada). **Anota la fecha de
vencimiento y ponla en un calendario**: si el dominio caduca, el DNS deja de resolver, Resend deja de poder enviar y
**nadie se puede registrar**. Hace falta que alguien tenga acceso al panel DNS del dominio (normalmente, quien lo registró).

**Procedimiento** **[Sin probar: requiere cuentas y DNS reales]**

1. **Cuenta de Resend** con un correo del equipo, no personal. Anota quién es el titular.
2. **Elige el subdominio de envío** (por ejemplo `mail.<dominio>`) y la dirección remitente (por ejemplo
   `no-reply@mail.<dominio>`).
3. *Resend → Domains → Add Domain* con ese subdominio. **Resend muestra los registros DNS exactos** (tipo, nombre y valor):
   cópialos tal cual al panel DNS del registrador (o pásaselos a quien lo administre) y pulsa *Verify*. La documentación
   consultada no dice cuánto tarda; puede ser desde minutos hasta horas, así que no lo dejes para el último momento. La
   región del dominio se elige al crearlo; no se comprobó cuáles ofrece.
4. *Resend → API Keys → Create API Key*, con permiso de **solo envío** y, si el panel lo permite, **limitada a ese dominio**.
   Crea **una clave por entorno** (`alivia-staging`, `alivia-production`) para poder revocarlas por separado. La clave se
   muestra una sola vez: cópiala directamente a `gh secret set` (sección 3.3).
5. **Carga los valores** (sección 3.3): `IAM_EMAIL_PROVIDER=resend`, `IAM_EMAIL_FROM=no-reply@mail.<dominio>`, opcionalmente
   `IAM_EMAIL_FROM_NAME=Alivia`, y el secreto `RESEND_API_KEY`.
6. **Deja que `deploy.sh` lo compruebe.** Con `IAM_EMAIL_PROVIDER=resend` exige `RESEND_API_KEY` e `IAM_EMAIL_FROM`; rechaza
   un remitente con nombre visible (`Alivia <...>`, que Resend rechazaría porque la aplicación ya añade el nombre) y avisa
   si el remitente es el predeterminado `alivia.com` o si la clave no empieza por `re_`. Con `smtp`, `log` o un valor
   desconocido no despliega.
7. **Prueba de extremo a extremo** tras el primer despliegue: registra un usuario con tu propio correo, comprueba que llega
   la verificación y que el enlace apunta al frontend (no a `localhost`: revisa `IAM_EMAIL_VERIFICATION_LINK_BASE_URL`).

**Mientras el dominio no esté verificado** (solo para probar el flujo de alta con **tu** correo, el de la cuenta de Resend):
Resend ofrece un remitente de prueba de su propio dominio, normalmente `onboarding@resend.dev`; no se verificó su nombre
exacto en la documentación consultada. Úsalo como `IAM_EMAIL_FROM` únicamente en `staging` y sustitúyelo por el del dominio
verificado antes de dar acceso a otras personas.

**Si el correo no sale:**

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El registro responde con error y el registro de la aplicación dice `Verification email delivery failed for account id ...` | El envío falló. **La aplicación solo escribe la clase de la excepción**; el motivo (403, 429...) está en el panel de Resend | *Resend → Logs* (o *Emails*) y busca el intento; `az webapp log tail` para ver la hora |
| 403 `domain is not verified` en Resend | El remitente usa un dominio sin verificar, o es el predeterminado `alivia.com` | Verifica el dominio o corrige `IAM_EMAIL_FROM` |
| 403 `only send testing emails to your own email address` | Se envía con el remitente de prueba o con un dominio sin verificar | Verifica el dominio |
| 429 o error tras muchos envíos | Se alcanzó el límite diario del plan gratuito | Espera al día siguiente o pasa de plan |
| La aplicación no arranca con `IAM_EMAIL_PROVIDER=resend` | Falta `RESEND_API_KEY` (la aplicación la exige al iniciar; `IAM_EMAIL_FROM` tiene un valor por defecto, por lo que su ausencia no impide arrancar, solo hace fallar el envío) | `deploy.sh` ya lo habría rechazado; revisa los valores del entorno |

### 2.2 Qué debe ser distinto entre `staging` y `production`

Los valores son **del entorno**, así que cada entorno guarda los suyos aunque coincidan. La regla de fondo: `staging` se
protege menos (sin revisores y desde cualquier rama), de modo que cualquiera con permiso de escritura puede ejecutar allí un
workflow con sus secretos. **Ningún secreto de `production` se carga nunca en `staging`**, ni siquiera «temporalmente».

| Grupo | Variables | Qué hacer |
|---|---|---|
| **Distintas por construcción** (salen de `terraform output` de cada entorno) | `AZURE_CLIENT_ID`, `AZURE_WEBAPP_NAME`, `DB_URL`, `DB_PASSWORD`, `MONGODB_URI` | Se leen en la carpeta de Terraform de cada entorno (sección 3.1); no hay forma de mezclarlas si se usa `TF_DIR` |
| **Deben ser distintas** (seguridad) | `IAM_JWT_SECRET`, `IAM_SUPER_ADMIN_PASSWORD`, `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`, `RESEND_API_KEY` | Se generan o se crean **por separado** para cada entorno (detalle abajo) |
| **Deben ser distintas** (apuntan al frontend de cada entorno) | `CORS_ALLOWED_ORIGINS`, `IAM_EMAIL_VERIFICATION_LINK_BASE_URL`, `IAM_ACCOUNT_ACTIVATION_LINK_BASE_URL` | La URL del frontend de ese entorno; en `production` un enlace de staging llevaría a las personas al entorno equivocado |
| **Conviene que sean distintas** | `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_CLOUD_NAME`, `IAM_EMAIL_FROM_NAME` | Cloudinary: con la misma cuenta, `staging` puede borrar o sobrescribir imágenes de `production`; no se verificó si el plan gratuito permite varias. `IAM_EMAIL_FROM_NAME`: por ejemplo `Alivia (staging)`, para que un correo de pruebas no se confunda con uno real |
| **Pueden ser iguales** (se cargan igualmente en los dos) | `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_RESOURCE_GROUP`, `ACR_NAME`, `DB_USERNAME`, `MONGODB_DATABASE`, `IAM_EMAIL_PROVIDER`, `IAM_EMAIL_FROM`, `IAM_SUPER_ADMIN_EMAIL`, `HCM_DEFAULT_BUSINESS_ID` y los ajustes opcionales | Mismo valor porque describen lo mismo: la misma suscripción, el mismo grupo y el mismo ACR, o porque son configuración sin riesgo |

**Por qué hay secretos que no pueden compartirse:**

- **`IAM_JWT_SECRET`.** Los tokens llevan `sub` (id de usuario), `email`, `role` y `permissions`, pero **ni emisor ni
  audiencia**: la clave de firma es lo único que separa los tokens de un entorno de los del otro. Con la misma clave, un
  token de `staging` pasa la comprobación de firma en `production`; que sea aceptado como un usuario concreto depende de que
  ese id exista allí, y el super administrador se crea al arrancar en los dos, así que es probable que coincidan.
  `openssl rand` se ejecuta una vez por entorno (sección 3.2), de modo que salen distintas.
- **`STRIPE_*`.** `staging` con claves de **prueba** (`sk_test_...`, `pk_test_...`) y `production` con las **reales**. El
  `whsec_...` además es **por endpoint**: cada entorno registra su propio endpoint en Stripe y tiene el suyo.
- **`RESEND_API_KEY`.** La misma cuenta y el mismo dominio verificado sirven para los dos, pero con **una clave por entorno**
  (sección 2.1): se pueden revocar por separado si una se filtra. `IAM_EMAIL_FROM` puede ser la misma dirección.
- **`IAM_SUPER_ADMIN_PASSWORD`.** La cuenta de administrador existe en las dos bases; una contraseña común haría que una
  filtración desde `staging` abriera `production`. El correo (`IAM_SUPER_ADMIN_EMAIL`) sí puede repetirse: es solo el
  identificador de acceso de cada base.
- **Contraseñas de las bases de datos.** Ya difieren, porque Terraform genera una por entorno.

**Comprobación.** Los secretos de GitHub no se pueden leer, así que no hay forma de comprobar después que dos son
distintos: se garantiza **al crearlos**, generando cada uno por separado y guardándolo en el gestor de contraseñas con el
nombre del entorno (`alivia-staging-jwt`, `alivia-production-jwt`). Las variables sí se pueden comparar:

```bash
for e in staging production; do echo "== $e"; gh variable list --env "$e" --repo "$R"; done
```

Revisa que `AZURE_CLIENT_ID`, `AZURE_WEBAPP_NAME`, `DB_URL`, `CORS_ALLOWED_ORIGINS` y los enlaces del frontend **no** sean
iguales en los dos.

## 3. Cargar los valores

> Los secretos de GitHub son **de solo escritura**: después de cargar uno no hay forma de leerlo. Guarda los que generes
> en un gestor de contraseñas **antes** de cargarlos. Nada de esto se escribe en un archivo del repositorio.

### 3.1 Los que salen de Terraform **[Sin probar]**

```bash
cd "$TF_DIR"

v() { gh variable set "$1" --env "$ENV" --repo "$R" --body "$2"; }

v AZURE_CLIENT_ID        "$(terraform output -raw deploy_identity_client_id)"
v AZURE_TENANT_ID        "$(terraform output -raw azure_tenant_id)"
v AZURE_SUBSCRIPTION_ID  "$(terraform output -raw azure_subscription_id)"
v AZURE_RESOURCE_GROUP   "$(terraform output -raw resource_group_name)"
v AZURE_WEBAPP_NAME      "$(terraform output -raw webapp_name)"
v ACR_NAME               "$(terraform output -raw acr_name)"
v DB_URL                 "$(terraform output -raw postgres_jdbc_url)"
v DB_USERNAME            "$(terraform output -raw postgres_administrator_login)"
v MONGODB_DATABASE       "$(terraform output -raw mongodb_database)"

# Los secretos se canalizan: nunca pasan por la pantalla ni por el historial.
terraform output -raw postgres_administrator_password | gh secret set DB_PASSWORD --env "$ENV" --repo "$R"
terraform output -raw mongodb_connection_string       | gh secret set MONGODB_URI --env "$ENV" --repo "$R"
cd - >/dev/null
```

### 3.2 Los que se generan **[Sin probar]**

```bash
# 64 caracteres aleatorios, más de los 32 bytes que exige la aplicación.
openssl rand -base64 48 | tr -d '\n' | gh secret set IAM_JWT_SECRET --env "$ENV" --repo "$R"

# Contraseña del super administrador: genérala UNA vez, guárdala y cárgala.
openssl rand -base64 24 | tr -d '\n' | tee /dev/tty | gh secret set IAM_SUPER_ADMIN_PASSWORD --env "$ENV" --repo "$R"
```

El segundo comando **muestra** la contraseña en pantalla una única vez (con `tee /dev/tty`) para que la copies a tu gestor.
Si prefieres elegirla tú, omite ese comando y usa el aviso interactivo de `gh secret set IAM_SUPER_ADMIN_PASSWORD ...`.

### 3.3 Los de terceros y los que dependen del frontend **[Sin probar]**

Sin `--body` ni canalización, `gh secret set` pide el valor con la entrada oculta:

```bash
for name in STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET CLOUDINARY_API_KEY CLOUDINARY_API_SECRET RESEND_API_KEY; do
  gh secret set "$name" --env "$ENV" --repo "$R"
done
```

Las variables (sustituye los valores):

```bash
v() { gh variable set "$1" --env "$ENV" --repo "$R" --body "$2"; }

v CORS_ALLOWED_ORIGINS                   "https://<frontend>"
v IAM_EMAIL_VERIFICATION_LINK_BASE_URL   "https://<frontend>/verify-email"
v IAM_ACCOUNT_ACTIVATION_LINK_BASE_URL   "https://<frontend>/activate-account"
v IAM_SUPER_ADMIN_EMAIL                  "<correo del administrador>"
v STRIPE_PUBLISHABLE_KEY                 "pk_test_..."
v CLOUDINARY_CLOUD_NAME                  "<cloud name>"
v IAM_EMAIL_PROVIDER                     "resend"
v IAM_EMAIL_FROM                         "<remitente verificado>"
```

Por la interfaz: *Settings → Environments → `<entorno>` → Environment secrets / Environment variables → Add*.

### 3.4 Comprobar que no falta nada **[Sin probar]**

```bash
need_vars="AZURE_CLIENT_ID AZURE_TENANT_ID AZURE_SUBSCRIPTION_ID AZURE_RESOURCE_GROUP AZURE_WEBAPP_NAME ACR_NAME \
DB_URL DB_USERNAME MONGODB_DATABASE CORS_ALLOWED_ORIGINS IAM_EMAIL_VERIFICATION_LINK_BASE_URL STRIPE_PUBLISHABLE_KEY \
CLOUDINARY_CLOUD_NAME IAM_SUPER_ADMIN_EMAIL"
need_secrets="DB_PASSWORD MONGODB_URI IAM_JWT_SECRET STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET CLOUDINARY_API_KEY \
CLOUDINARY_API_SECRET IAM_SUPER_ADMIN_PASSWORD"

have_vars=$(gh variable list --env "$ENV" --repo "$R" --json name --jq '.[].name')
have_secrets=$(gh secret list --env "$ENV" --repo "$R" --json name --jq '.[].name')
for n in $need_vars;    do grep -qx "$n" <<<"$have_vars"    || echo "FALTA variable: $n"; done
for n in $need_secrets; do grep -qx "$n" <<<"$have_secrets" || echo "FALTA secreto:  $n"; done
echo "comprobación terminada"
```

No debe aparecer ningún `FALTA`. Esto solo comprueba que el nombre existe, no que el valor sea correcto: el valor lo
prueba el primer despliegue. Las variables sí se pueden leer (`gh variable list --env "$ENV" --repo "$R"`); revisa que
`AZURE_WEBAPP_NAME` y `AZURE_CLIENT_ID` sean los del entorno correcto.

## 4. Orden de la primera prueba (importante)

Dos reglas de GitHub condicionan cómo se prueba, y la segunda sorprende:

1. **Un `push` usa el archivo del workflow que está en el commit enviado.** Por eso el primer despliegue se prueba
   **fusionando en `develop`**: el push dispara `staging` aunque el workflow aún no esté en `main`.
2. **`workflow_dispatch` exige que el workflow esté en la rama por defecto (`main`).** Hasta que `cd-deploy.yml` llegue a
   `main`, el botón «Run workflow» y `gh workflow run` no lo encuentran, y no se puede promover una imagen a `production`
   con `image_tag`.
3. **Fusionar a `main` lanza un despliegue a `production`** (el archivo del workflow coincide con el filtro de rutas).
   Antes de esa fusión: `production` aplicado en Terraform, el entorno creado con revisores y sus valores cargados, o el
   revisor tendrá que **rechazar** esa ejecución.

Secuencia recomendada:

1. Aplicar la identidad en `staging` (`terraform apply`), cargar `staging` (secciones 1 a 3).
2. Fusionar la rama de CD en `develop` por *pull request* → se dispara el despliegue a `staging` (sección 5).
3. Con `staging` verificado, aplicar `production` en Terraform y cargar `production` (sección 6).
4. Fusionar `develop` en `main` → despliegue a `production`, con aprobación.

## 5. Primer despliegue a `staging`

### 5.1 Antes de fusionar

- **Stripe (modo de prueba):** crea el endpoint `https://<default_hostname>/api/v1/webhooks/stripe` en *Developers →
  Webhooks* y copia su `whsec_...` a `STRIPE_WEBHOOK_SECRET`. `default_hostname` sale de
  `terraform output -raw default_hostname`; el endpoint puede registrarse antes de que la API exista.
- **Protección de ramas (R-08):** exige el chequeo `Check - Maven Test Suite` en `develop` y `main`; el CD no repite las
  pruebas.
- **Negocio inicial (R-07, sin resolver):** el inventario necesita que exista el negocio `HCM_DEFAULT_BUSINESS_ID` (por
  defecto 1) y ninguna migración lo crea. Hasta decidir cómo se crea, registrar un dispositivo devolverá 404.

### 5.2 Disparar y seguir **[Sin probar]**

```bash
# tras fusionar la rama de CD en develop:
gh run list --repo "$R" --workflow cd-deploy.yml --limit 3
gh run watch --repo "$R"                    # elige la ejecución en curso
```

Resultado esperado (resumen de la ejecución, pestaña *Summary*): `Deployment plan` con el entorno y la etiqueta, y al final
`Despliegue correcto` con la URL.

### 5.3 Comprobar a mano

```bash
HOST=$(cd infrastructure/terraform/environments/staging && terraform output -raw default_hostname)
curl -s "https://$HOST/actuator/health"                       # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}\n' "https://$HOST/api/v1/plans"          # 200
curl -s -o /dev/null -w '%{http_code}\n' "https://$HOST/v3/api-docs"           # 404 o 401
curl -s -o /dev/null -w '%{http_code}\n' "https://$HOST/api/v1/devices"        # 401
az webapp log tail -g rg-alivia -n "$(cd infrastructure/terraform/environments/staging && terraform output -raw webapp_name)"
```

Para medir la latencia real desde Lima, cronometra `GET /actuator/health` desde allí.

### 5.4 Si falla

| Mensaje o síntoma | Causa probable | Qué hacer |
|---|---|---|
| `The GitHub environment 'staging' lacks these variables: ...` | Falta una variable de Azure en el entorno | Sección 3.1 y 3.4 |
| `faltan variables obligatorias: ...` (de `deploy.sh`) | Falta un secreto o una variable de la aplicación | Sección 3.2, 3.3 y 3.4 |
| `AADSTS70025` o `No matching federated identity record found` en `azure/login` | El sujeto no coincide: nombre del entorno, mayúsculas del repositorio, o `AZURE_CLIENT_ID` de otro entorno | Compara el sujeto del error con `terraform output` y la variable `github_repository` |
| `AuthorizationFailed` al publicar la imagen o al desplegar | Los roles de la identidad aún no se han propagado, o el `apply` de la identidad no se hizo | Espera unos minutos y reintenta; comprueba los dos roles en el portal |
| Falla el paso *Scan image* | Vulnerabilidad CRITICAL o HIGH **con corrección** | Actualiza la dependencia o la imagen base; no se desactiva el análisis |
| `la imagen alivia-api:<tag> no existe en el registro` | Se dio un `image_tag` que no existe | Lista las etiquetas: `az acr repository show-tags -n <acr> --repository alivia-api` |
| `la aplicacion no llego a UP` | La app no arranca: configuración, PostgreSQL inaccesible (R-06) o una migración fallida | `az webapp log tail`; el script ya restauró la imagen anterior si la había |
| `la imagen anterior ... no esta en el registro (primer despliegue?)` | Es el primer despliegue: la Web App nace con `alivia-api:initial`, que no existe | Normal; no hay nada que restaurar |
| El despliegue a `production` no pide aprobación | El entorno no tiene revisores | Detén la ejecución y corrige la sección 1 (R-16) |

## 6. `production`

### 6.1 Terraform

```bash
cd infrastructure/terraform/environments/production
cp terraform.tfvars.example terraform.tfvars        # edita: acr_name, atlas_organization_id, postgres_version, SKU...
terraform init
terraform apply -target=module.platform             # primera vez: la Web App y sus IP de salida
terraform plan -out tfplan
terraform apply tfplan
terraform plan                                      # debe decir "No changes"
```

Copia `terraform.tfstate` fuera del repositorio y borra `tfplan` (contiene valores sensibles). Los marcadores del
`tfvars` (`acralivia00000`, `000000000000000000000000`) tienen que sustituirse (§6.5 de `azure-deployment.md`). Production
crea su propio proyecto y clúster de Atlas (M0, un clúster gratuito por proyecto) y duplica el costo de App Service y
PostgreSQL: valora un clúster de pago si hay presupuesto (R-09, el M0 no tiene copias de seguridad).

### 6.2 Entorno y valores

Repite las secciones 1 y 3 con `ENV=production`, **volviendo a generar** los secretos de la sección 3.2 (no se copian de
staging) y usando **claves de producción de Stripe** y valores propios: el detalle de qué cambia y por qué está en la
sección 2.2. El `AZURE_CLIENT_ID` es el de `id-alivia-production-deploy`, no el de staging. Registra en Stripe el endpoint de
`production` (su propio `whsec_...`) y crea en Resend una clave propia de `production`.

### 6.3 Comprobar que la protección funciona

Lanza un despliegue a `production` desde una rama que no sea `main` (solo posible cuando el workflow esté en `main`):

```bash
gh workflow run cd-deploy.yml --repo "$R" --ref develop -f environment=production
```

Debe fallar en `resolve` con *Production is deployed only from main*. Después, un push a `main` debe quedar **esperando
aprobación** en la pestaña *Actions* hasta que un revisor la conceda.

### 6.4 Promover la imagen probada en staging

El push a `main` construye una imagen **nueva** (su SHA es el del commit de fusión, distinto del de `develop`). Para
desplegar **la misma imagen** que se probó en `staging`, una vez el workflow esté en `main`:

```bash
STAGING_TAG="pega-aqui-el-sha-completo-de-staging"
gh workflow run cd-deploy.yml --repo "$R" --ref main -f environment=production -f image_tag="$STAGING_TAG"
```

La etiqueta es el SHA completo que aparece en el resumen del despliegue a `staging`. No se construye ni se analiza: solo se
despliega. Hay que aprobar el entorno `production`.

## 7. Mantenimiento

| Qué | Cuándo | Cómo |
|---|---|---|
| Cambiar un valor | Cuando cambia el origen | `gh variable set` / `gh secret set` en el entorno y volver a desplegar: `deploy.sh` copia los valores en cada ejecución |
| Rotar la contraseña de PostgreSQL | Periódicamente o ante sospecha | `terraform apply -replace=module.postgres.random_password.administrator`, cargar `DB_PASSWORD` y redesplegar |
| Rotar la contraseña del usuario de Atlas | Periódicamente o ante sospecha | `terraform apply -replace=module.mongodb_atlas.random_password.application`, cargar `MONGODB_URI` (lleva la contraseña) y redesplegar |
| Rotar `IAM_JWT_SECRET` | Ante sospecha | Cierra todas las sesiones: hazlo fuera de horario |
| La identidad de despliegue | — | No tiene secreto ni vencimiento |
| Cambió el repositorio de dueño o de nombre | — | Variable `github_repository` de Terraform y `terraform apply` en los dos entornos |
| Apagar `staging` para ahorrar créditos | Fuera de uso | `az webapp stop` y `az postgres flexible-server stop` (Azure reinicia el servidor a los 7 días) |
