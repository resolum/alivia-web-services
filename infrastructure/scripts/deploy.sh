#!/usr/bin/env bash
#
# Deploys one image of the Alivia API to the App Service of an environment and verifies the result.
#
#   infrastructure/scripts/deploy.sh <staging|production> <image-tag>
#
# What it does (docs/operations/azure-deployment.md, section 8):
#   1. validates the arguments, that every required variable is set (it lists NAMES, never values) and that the email
#      configuration can work;
#   2. checks that the image exists in the registry and remembers the one that runs now;
#   3. applies the application settings and the new image, and restarts the Web App;
#   4. waits for /actuator/health to report UP, then checks the public routes;
#   5. if step 4 fails, puts the previous image back and exits with status 1.
#
# Azure login is NOT handled here: the caller (the workflow, or a person running `az login`) must already have a session.
# Flyway migrations run when the application starts and are never undone, so going back to the previous image is only
# safe when the new migrations are compatible with it (see the deployment document, section 11.2).
# Application settings are not restored on a rollback: only the image is.
#
# Required environment (names of the resources, from `terraform output`):
#   AZURE_RESOURCE_GROUP, AZURE_WEBAPP_NAME, ACR_NAME
# plus every variable in REQUIRED_APP_VARS below. Optional tuning variables in OPTIONAL_APP_VARS are copied when set.
#
# Tuning for tests and slow environments (all in seconds):
#   DEPLOY_WARMUP_SECONDS (30), DEPLOY_HEALTH_TIMEOUT_SECONDS (600), DEPLOY_HEALTH_INTERVAL_SECONDS (10)

set -euo pipefail

# Bytes, not characters: the JWT secret has to be at least 32 bytes, and control characters are checked bytewise.
export LC_ALL=C

readonly IMAGE_REPOSITORY="alivia-api"
readonly WARMUP_SECONDS="${DEPLOY_WARMUP_SECONDS:-30}"
readonly HEALTH_TIMEOUT_SECONDS="${DEPLOY_HEALTH_TIMEOUT_SECONDS:-600}"
readonly HEALTH_INTERVAL_SECONDS="${DEPLOY_HEALTH_INTERVAL_SECONDS:-10}"

readonly RESOURCE_VARS=(AZURE_RESOURCE_GROUP AZURE_WEBAPP_NAME ACR_NAME)

# No default in the prod profile, or a default that must never reach production (IAM_SUPER_ADMIN_* default to the
# placeholders EMAIL and PASSWORD).
readonly REQUIRED_APP_VARS=(
  DB_URL DB_USERNAME DB_PASSWORD
  MONGODB_URI MONGODB_DATABASE
  IAM_JWT_SECRET
  CORS_ALLOWED_ORIGINS IAM_EMAIL_VERIFICATION_LINK_BASE_URL
  STRIPE_SECRET_KEY STRIPE_PUBLISHABLE_KEY STRIPE_WEBHOOK_SECRET
  CLOUDINARY_CLOUD_NAME CLOUDINARY_API_KEY CLOUDINARY_API_SECRET
  IAM_SUPER_ADMIN_EMAIL IAM_SUPER_ADMIN_PASSWORD
)

# Have a default in the application; they are copied only when set.
readonly OPTIONAL_APP_VARS=(
  IAM_EMAIL_PROVIDER IAM_EMAIL_FROM IAM_EMAIL_FROM_NAME RESEND_API_KEY
  IAM_ACCOUNT_ACTIVATION_LINK_BASE_URL IAM_ACCOUNT_ACTIVATION_EXPIRATION_HOURS
  IAM_EMAIL_VERIFICATION_EXPIRATION_HOURS IAM_EMAIL_VERIFICATION_RESEND_COOLDOWN_SECONDS
  IAM_DEFAULT_PHOTO_URL HCM_DEFAULT_BUSINESS_ID
  IAM_JWT_ACCESS_TOKEN_EXPIRATION_MINUTES IAM_JWT_REFRESH_TOKEN_EXPIRATION_DAYS
  IAM_EDGE_CREDENTIAL_ROTATION_RECOMMENDED_AFTER IAM_EDGE_CREDENTIAL_MAX_FAILED_ATTEMPTS_PER_MINUTE
  MULTIPART_MAX_FILE_SIZE JAVA_TOOL_OPTIONS
  DOCUMENTATION_APPLICATION_DESCRIPTION DOCUMENTATION_APPLICATION_VERSION
)

# Defaults in the application that are wrong for a deployed environment: worth a warning when not set. The email
# provider has its own checks (check_email_configuration).
readonly WARN_IF_UNSET=(IAM_ACCOUNT_ACTIVATION_LINK_BASE_URL)

# Never printed; masked in GitHub Actions logs.
readonly SECRET_VARS=(
  DB_PASSWORD MONGODB_URI IAM_JWT_SECRET
  STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET
  CLOUDINARY_API_KEY CLOUDINARY_API_SECRET
  IAM_SUPER_ADMIN_PASSWORD RESEND_API_KEY
)

# Owned by this script (Terraform does not define them). MongoDB is left out of /actuator/health while no code uses it,
# so an Atlas outage cannot fail a deployment (decision P-7). The property is management.health.mongodb.enabled.
readonly FIXED_SETTINGS=(
  "SPRING_PROFILES_ACTIVE=prod"
  "PORT=8080"
  "WEBSITES_PORT=8080"
  "SERVER_FORWARD_HEADERS_STRATEGY=framework"
  "MANAGEMENT_HEALTH_MONGODB_ENABLED=false"
)

ENVIRONMENT=""
IMAGE_TAG=""
LOGIN_SERVER=""
TARGET_IMAGE=""
PREVIOUS_IMAGE=""
BASE_URL=""
SETTINGS_FILE=""

log() { printf '%s\n' "$*"; }
warn() { printf 'AVISO: %s\n' "$*" >&2; }
die() {
  printf 'ERROR: %s\n' "$*" >&2
  write_summary "Despliegue fallido" "$*"
  exit 1
}
usage_error() {
  printf 'ERROR: %s\n' "$*" >&2
  printf 'Uso: %s <staging|production> <etiqueta-de-imagen>\n' "$0" >&2
  exit 2
}

# Adds a section to the run summary of GitHub Actions; harmless anywhere else.
write_summary() {
  [[ -n "${GITHUB_STEP_SUMMARY:-}" ]] || return 0
  {
    printf '### %s\n\n' "$1"
    printf '%s\n' "- Entorno: \`${ENVIRONMENT:-?}\`" "- Imagen: \`${IMAGE_TAG:-?}\`" "- ${2}"
  } >>"$GITHUB_STEP_SUMMARY" || true
}

cleanup() {
  if [[ -n "$SETTINGS_FILE" ]]; then
    rm -f "$SETTINGS_FILE"
  fi
}
trap cleanup EXIT

is_set() {
  local name="$1"
  [[ -n "${!name:-}" ]]
}

contains() {
  local needle="$1" item
  shift
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

# ---------------------------------------------------------------------------------------------------------------------
# 1. Arguments and variables
# ---------------------------------------------------------------------------------------------------------------------

parse_arguments() {
  [[ $# -eq 2 ]] || usage_error "se esperaban 2 argumentos y llegaron $#"
  ENVIRONMENT="$1"
  IMAGE_TAG="$2"

  case "$ENVIRONMENT" in
    staging | production) ;;
    *) usage_error "entorno desconocido: '$ENVIRONMENT' (staging o production)" ;;
  esac

  # P-2: images are tagged with the commit; a moving tag would make a rollback meaningless.
  [[ "$IMAGE_TAG" != "latest" ]] || usage_error "no se despliega 'latest': usa la etiqueta del commit"
  [[ "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || usage_error "etiqueta de imagen no valida"
}

check_tools() {
  local tool
  for tool in az curl; do
    command -v "$tool" >/dev/null 2>&1 || die "falta la herramienta '$tool' en el PATH"
  done
}

# Email decides whether people can register: sign-up and invitations fail when the message cannot be sent
# (RegistrationUnavailableException). The prod profile accepts resend, smtp and unavailable. "log" is development only
# and leaves the application without an email sender, so it would not start, and smtp needs spring.mail.* properties that
# this script does not copy. Every combination that cannot work is refused here, before anything is changed.
check_email_configuration() {
  local provider="${IAM_EMAIL_PROVIDER:-}" name
  local missing=()
  local address_pattern='^[^@[:space:]<>]+@[^@[:space:]<>]+\.[^@[:space:]<>]+$'

  case "$provider" in
    resend)
      for name in RESEND_API_KEY IAM_EMAIL_FROM; do
        is_set "$name" || missing+=("$name")
      done
      ((${#missing[@]} == 0)) || die "IAM_EMAIL_PROVIDER=resend exige estas variables: ${missing[*]}"
      # The application writes "<name> <address>" itself: an address that already carries a name is rejected by Resend.
      [[ "$IAM_EMAIL_FROM" =~ $address_pattern ]] \
        || die "IAM_EMAIL_FROM debe ser solo una direccion (nombre@dominio); el nombre visible va en IAM_EMAIL_FROM_NAME"
      [[ "$RESEND_API_KEY" == re_* ]] || warn "RESEND_API_KEY no empieza por 're_', como las claves de Resend"
      [[ "${IAM_EMAIL_FROM##*@}" != "alivia.com" ]] \
        || warn "IAM_EMAIL_FROM usa alivia.com, el valor por defecto de la aplicacion: Resend rechaza el envio si ese dominio no esta verificado en la cuenta"
      ;;
    "")
      warn "IAM_EMAIL_PROVIDER no esta definida: el perfil prod usa 'unavailable' y el registro y las invitaciones de usuarios fallaran"
      ;;
    unavailable)
      warn "IAM_EMAIL_PROVIDER=unavailable: ningun correo sale, y el registro y las invitaciones de usuarios fallaran"
      ;;
    smtp)
      die "IAM_EMAIL_PROVIDER=smtp no esta soportado por este script: no copia las propiedades spring.mail.*. Usa resend"
      ;;
    *)
      die "IAM_EMAIL_PROVIDER='$provider' no es valido en prod (usa resend)"
      ;;
  esac
}

check_variables() {
  local missing=() name
  for name in "${RESOURCE_VARS[@]}" "${REQUIRED_APP_VARS[@]}"; do
    is_set "$name" || missing+=("$name")
  done
  if ((${#missing[@]} > 0)); then
    die "faltan variables obligatorias: ${missing[*]}"
  fi

  # Guard against pointing a deployment at the other environment's Web App (names are app-alivia-<env>-<suffix>).
  [[ "$AZURE_WEBAPP_NAME" == "app-alivia-${ENVIRONMENT}-"* ]] \
    || die "AZURE_WEBAPP_NAME no pertenece al entorno '$ENVIRONMENT'"

  [[ "${#IAM_JWT_SECRET}" -ge 32 ]] || die "IAM_JWT_SECRET debe tener al menos 32 bytes"
  [[ "$IAM_SUPER_ADMIN_EMAIL" != "EMAIL" && "$IAM_SUPER_ADMIN_PASSWORD" != "PASSWORD" ]] \
    || die "IAM_SUPER_ADMIN_EMAIL e IAM_SUPER_ADMIN_PASSWORD conservan el valor de marcador de posicion"

  for name in "${WARN_IF_UNSET[@]}"; do
    is_set "$name" || warn "$name no esta definida: se usara el valor por defecto de la aplicacion, pensado para desarrollo"
  done

  check_email_configuration
}

# In GitHub Actions the values of secrets are already masked, but a variable that holds a derived value (for example a
# connection string assembled by hand) is not: mask every secret this script handles, line by line.
mask_secrets() {
  [[ "${GITHUB_ACTIONS:-}" == "true" ]] || return 0
  local name line
  for name in "${SECRET_VARS[@]}"; do
    is_set "$name" || continue
    while IFS= read -r line; do
      if [[ -n "$line" ]]; then
        printf '::add-mask::%s\n' "$line"
      fi
    done <<<"${!name}"
  done
}

# ---------------------------------------------------------------------------------------------------------------------
# Application settings file
# ---------------------------------------------------------------------------------------------------------------------

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

# Control characters other than newline, carriage return and tab cannot be written into the file as they are.
reject_control_characters() {
  local name="$1" value="$2"
  if [[ "$value" == *[$'\x01'-$'\x08'$'\x0b'$'\x0c'$'\x0e'-$'\x1f'$'\x7f']* ]]; then
    die "$name contiene caracteres de control no admitidos"
  fi
}

# The native path for the Azure CLI: under Git Bash on Windows az is a Windows program and cannot read /tmp/...
native_path() {
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -m "$1"
  else
    printf '%s' "$1"
  fi
}

# Writes the settings as the JSON file `az webapp config appsettings set --settings @file` reads. The values never go
# on a command line (where `ps` would show them): the file is readable only by this user and is removed on exit.
write_settings_file() {
  local file="$1" pair name value first=1
  local entries=()

  for pair in "${FIXED_SETTINGS[@]}"; do
    entries+=("$pair")
  done
  for name in "${REQUIRED_APP_VARS[@]}" "${OPTIONAL_APP_VARS[@]}"; do
    is_set "$name" || continue
    entries+=("$name=${!name}")
  done

  {
    printf '['
    for pair in "${entries[@]}"; do
      name="${pair%%=*}"
      value="${pair#*=}"
      reject_control_characters "$name" "$value"
      ((first)) || printf ','
      first=0
      printf '\n  {"name":"%s","value":"%s","slotSetting":false}' "$name" "$(json_escape "$value")"
    done
    printf '\n]\n'
  } >"$file"
}

# ---------------------------------------------------------------------------------------------------------------------
# Azure operations
# ---------------------------------------------------------------------------------------------------------------------

check_azure_session() {
  az account show -o none >/dev/null 2>&1 || die "az no tiene una sesion iniciada (az login)"
}

image_in_registry() {
  az acr repository show -n "$ACR_NAME" --image "${IMAGE_REPOSITORY}:$1" -o none >/dev/null 2>&1
}

# The image that runs now, without the "DOCKER|" prefix of linuxFxVersion.
current_image() {
  local fx
  fx="$(az webapp config show -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_WEBAPP_NAME" --query linuxFxVersion -o tsv)"
  printf '%s' "${fx#DOCKER|}"
}

set_image() {
  az webapp config set -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_WEBAPP_NAME" \
    --linux-fx-version "DOCKER|$1" -o none
}

restart_app() {
  az webapp restart -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_WEBAPP_NAME" -o none
}

apply_settings() {
  # umask keeps the file private for as long as it exists (it has no effect on Windows file systems).
  local old_umask
  old_umask="$(umask)"
  umask 077
  SETTINGS_FILE="$(mktemp)"
  umask "$old_umask"

  write_settings_file "$SETTINGS_FILE"
  # -o none: by default az prints every setting of the app, secrets included.
  az webapp config appsettings set -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_WEBAPP_NAME" \
    --settings "@$(native_path "$SETTINGS_FILE")" -o none
  rm -f "$SETTINGS_FILE"
  SETTINGS_FILE=""
}

# ---------------------------------------------------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------------------------------------------------

wait_for_health() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS)) body
  while ((SECONDS < deadline)); do
    if body="$(curl -fsS --max-time 10 "$BASE_URL/actuator/health" 2>/dev/null)" \
      && [[ "$body" =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL_SECONDS"
  done
  return 1
}

http_status() {
  curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "$BASE_URL$1" 2>/dev/null || printf '000'
}

# $1 path, $2 expectation: "200" or "closed" (anything but a 2xx answer).
check_route() {
  local path="$1" expectation="$2" status
  status="$(http_status "$path")"
  case "$expectation" in
    200) [[ "$status" == "200" ]] ;;
    closed) [[ "$status" != 2* ]] ;;
  esac || {
    warn "$path respondio $status (se esperaba: $expectation)"
    return 1
  }
  log "  OK  $path -> $status"
}

verify_deployment() {
  local running
  running="$(current_image)"
  [[ "$running" == "$TARGET_IMAGE" ]] || {
    warn "la Web App ejecuta '$running' y no '$TARGET_IMAGE'"
    return 1
  }

  log "Esperando a que ${BASE_URL}/actuator/health responda UP (hasta ${HEALTH_TIMEOUT_SECONDS} s)"
  wait_for_health || {
    warn "la aplicacion no llego a UP"
    return 1
  }
  log "  OK  /actuator/health -> UP"

  check_route /actuator/info 200 || return 1
  check_route /api/v1/plans 200 || return 1 # a public route that reads PostgreSQL
  # Swagger is off in production and protected routes must refuse an anonymous caller.
  check_route /v3/api-docs closed || return 1
  check_route /api/v1/devices closed || return 1
}

# ---------------------------------------------------------------------------------------------------------------------
# Rollback
# ---------------------------------------------------------------------------------------------------------------------

rollback() {
  if [[ -z "$PREVIOUS_IMAGE" || "$PREVIOUS_IMAGE" == "$TARGET_IMAGE" ]]; then
    warn "no hay una imagen anterior distinta que restaurar"
    return 1
  fi
  if [[ "$PREVIOUS_IMAGE" != "${LOGIN_SERVER}/${IMAGE_REPOSITORY}:"* ]] \
    || ! image_in_registry "${PREVIOUS_IMAGE##*:}"; then
    warn "la imagen anterior ('$PREVIOUS_IMAGE') no esta en el registro: no se puede restaurar (primer despliegue?)"
    return 1
  fi

  log "Restaurando la imagen anterior: $PREVIOUS_IMAGE"
  set_image "$PREVIOUS_IMAGE"
  restart_app
  sleep "$WARMUP_SECONDS"
  if wait_for_health; then
    log "Imagen anterior restaurada y en UP"
  else
    warn "la imagen anterior restaurada tampoco llego a UP: revisa la Web App (az webapp log tail)"
  fi
}

# ---------------------------------------------------------------------------------------------------------------------

main() {
  parse_arguments "$@"
  check_tools
  check_variables
  mask_secrets
  check_azure_session

  LOGIN_SERVER="${ACR_NAME}.azurecr.io"
  TARGET_IMAGE="${LOGIN_SERVER}/${IMAGE_REPOSITORY}:${IMAGE_TAG}"

  log "Desplegando $TARGET_IMAGE en '$ENVIRONMENT' (${AZURE_WEBAPP_NAME})"

  image_in_registry "$IMAGE_TAG" || die "la imagen ${IMAGE_REPOSITORY}:${IMAGE_TAG} no existe en el registro $ACR_NAME"

  PREVIOUS_IMAGE="$(current_image)"
  log "Imagen en ejecucion: ${PREVIOUS_IMAGE:-ninguna}"

  BASE_URL="https://$(az webapp show -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_WEBAPP_NAME" --query defaultHostName -o tsv)"

  apply_settings
  set_image "$TARGET_IMAGE"
  restart_app

  # The old container keeps answering for a few seconds after a restart: without a pause the first health check could
  # be answered by the version that is being replaced.
  sleep "$WARMUP_SECONDS"

  if ! verify_deployment; then
    printf 'ERROR: la verificacion del despliegue fallo.\n' >&2
    rollback || true
    write_summary "Despliegue fallido" "La verificacion fallo; ver el registro de la ejecucion."
    exit 1
  fi

  log "Desplegado ${IMAGE_TAG} en ${ENVIRONMENT}: ${BASE_URL}"
  write_summary "Despliegue correcto" "URL: ${BASE_URL}"
}

main "$@"
