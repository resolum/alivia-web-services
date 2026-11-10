#!/usr/bin/env bash
#
# Stops the Web App and the PostgreSQL server of an environment, to save cost while nobody uses it.
#
#   infrastructure/scripts/stop-environment.sh <staging|production>
#
# What it does:
#   1. validates the argument and that the resource names belong to the environment;
#   2. stops the Web App first, so the application is not left running against a database that disappears;
#   3. stops the PostgreSQL server when it is Ready (already Stopped is fine; a state in transition is waited for).
#
# It is safe to run twice. `deploy.sh` starts both again on the next deployment (the Web App is restarted by
# `az webapp restart` and PostgreSQL by ensure_database_running).
#
# Azure login is NOT handled here: the caller (the workflow, or a person running `az login`) must already have a session.
#
# Required environment (names of the resources, from `terraform output`):
#   AZURE_RESOURCE_GROUP, AZURE_WEBAPP_NAME, AZURE_POSTGRES_SERVER_NAME
#
# Tuning for tests and slow environments (seconds): STOP_DATABASE_TIMEOUT_SECONDS (900), STOP_DATABASE_INTERVAL_SECONDS (15)

set -euo pipefail

readonly DATABASE_TIMEOUT_SECONDS="${STOP_DATABASE_TIMEOUT_SECONDS:-900}"
readonly DATABASE_INTERVAL_SECONDS="${STOP_DATABASE_INTERVAL_SECONDS:-15}"

ENVIRONMENT=""

log() { printf '%s\n' "$*"; }

write_summary() {
  [[ -n "${GITHUB_STEP_SUMMARY:-}" ]] || return 0
  {
    printf '### %s\n\n' "$1"
    printf '%s\n' "- Entorno: \`${ENVIRONMENT:-?}\`" "- ${2}"
  } >>"$GITHUB_STEP_SUMMARY" || true
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  write_summary "Apagado fallido" "$*"
  exit 1
}

usage_error() {
  printf 'ERROR: %s\n' "$*" >&2
  printf 'Uso: %s <staging|production>\n' "$0" >&2
  exit 2
}

check_inputs() {
  [[ $# -eq 1 ]] || usage_error "se esperaba 1 argumento y llegaron $#"
  ENVIRONMENT="$1"
  case "$ENVIRONMENT" in
    staging | production) ;;
    *) usage_error "entorno desconocido: '$ENVIRONMENT' (staging o production)" ;;
  esac

  command -v az >/dev/null 2>&1 || die "falta la herramienta 'az' en el PATH"

  local missing=() name
  for name in AZURE_RESOURCE_GROUP AZURE_WEBAPP_NAME AZURE_POSTGRES_SERVER_NAME; do
    [[ -n "${!name:-}" ]] || missing+=("$name")
  done
  ((${#missing[@]} == 0)) || die "faltan variables obligatorias: ${missing[*]}"

  # Same guards as deploy.sh: never stop the other environment's resources.
  [[ "$AZURE_WEBAPP_NAME" == "app-alivia-${ENVIRONMENT}-"* ]] \
    || die "AZURE_WEBAPP_NAME no pertenece al entorno '$ENVIRONMENT'"
  [[ "$AZURE_POSTGRES_SERVER_NAME" == "psql-alivia-${ENVIRONMENT}-"* ]] \
    || die "AZURE_POSTGRES_SERVER_NAME no pertenece al entorno '$ENVIRONMENT'"

  az account show -o none >/dev/null 2>&1 || die "az no tiene una sesion iniciada (az login)"
}

stop_webapp() {
  log "Deteniendo la Web App ${AZURE_WEBAPP_NAME}"
  az webapp stop -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_WEBAPP_NAME" -o none \
    || die "no se pudo detener la Web App '${AZURE_WEBAPP_NAME}'"
  log "  OK  Web App detenida"
}

stop_database() {
  local deadline=$((SECONDS + DATABASE_TIMEOUT_SECONDS)) state
  log "Deteniendo PostgreSQL ${AZURE_POSTGRES_SERVER_NAME}"
  while ((SECONDS < deadline)); do
    state="$(az postgres flexible-server show -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_POSTGRES_SERVER_NAME" \
      --query state -o tsv 2>/dev/null)" || die "no se pudo leer el estado de PostgreSQL '${AZURE_POSTGRES_SERVER_NAME}'"
    case "$state" in
      Stopped)
        log "  OK  PostgreSQL ya estaba detenido"
        return 0
        ;;
      Ready)
        az postgres flexible-server stop -g "$AZURE_RESOURCE_GROUP" -n "$AZURE_POSTGRES_SERVER_NAME" -o none \
          || die "no se pudo detener PostgreSQL '${AZURE_POSTGRES_SERVER_NAME}'"
        log "  OK  PostgreSQL detenido"
        return 0
        ;;
      Starting | Stopping | Updating)
        log "  PostgreSQL en estado ${state}: esperando"
        sleep "$DATABASE_INTERVAL_SECONDS"
        ;;
      *) die "PostgreSQL esta en un estado inesperado: '${state}'" ;;
    esac
  done
  die "PostgreSQL no salio del estado de transicion en ${DATABASE_TIMEOUT_SECONDS} s"
}

main() {
  check_inputs "$@"
  stop_webapp
  stop_database
  write_summary "Entorno apagado" "Web App \`${AZURE_WEBAPP_NAME}\` y PostgreSQL \`${AZURE_POSTGRES_SERVER_NAME}\` detenidos. El siguiente despliegue los enciende."
}

main "$@"
