#!/usr/bin/env bash
#
# Tests of stop-environment.sh with a fake `az`: nothing here touches Azure.
#
#   infrastructure/scripts/tests/stop_environment_test.sh
#
# What they cannot show: that the real `az` accepts these arguments, that the role of the deployment identity is enough
# to stop the server, or how a real server reports its state. That is checked by running the workflow against staging.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STOP="$HERE/../stop-environment.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- fake az ----------------------------------------------------------------------------------------------------------
# State lives in $FAKE_DIR: db_state (default Ready), calls (every az call). FAKE_*_FAILS make that operation fail.
mkdir -p "$WORK/bin"
cat >"$WORK/bin/az" <<'FAKE_AZ'
#!/usr/bin/env bash
echo "az $*" >>"$FAKE_DIR/calls"
args="$*"
case "$args" in
  "account show"*) [[ -z "${FAKE_NO_SESSION:-}" ]] ;;
  "webapp stop"*) [[ -z "${FAKE_WEBAPP_STOP_FAILS:-}" ]] ;;
  "postgres flexible-server show"*)
    state="$(cat "$FAKE_DIR/db_state" 2>/dev/null || echo Ready)"
    # A server in transition settles on the next query, as a real one does.
    case "$state" in
      Starting) echo Ready >"$FAKE_DIR/db_state" ;;
      Stopping) echo Stopped >"$FAKE_DIR/db_state" ;;
    esac
    echo "$state" ;;
  "postgres flexible-server stop"*)
    [[ -z "${FAKE_DB_STOP_FAILS:-}" ]] || exit 1
    echo Stopped >"$FAKE_DIR/db_state" ;;
  *) echo "fake az: unexpected call: $args" >&2; exit 99 ;;
esac
FAKE_AZ
chmod +x "$WORK/bin/az"

# --- harness ----------------------------------------------------------------------------------------------------------
PASSED=0
FAILED=0
pass() { PASSED=$((PASSED + 1)); printf '  ok   %s\n' "$1"; }
fail() { FAILED=$((FAILED + 1)); printf '  FAIL %s\n' "$1"; }
check() { # check "description" command...
  local description="$1"
  shift
  if "$@"; then pass "$description"; else fail "$description"; fi
}

new_case() { # new_case [db-state]
  FAKE_DIR="$WORK/case.$RANDOM"
  mkdir -p "$FAKE_DIR"
  : >"$FAKE_DIR/calls"
  [[ $# -eq 0 ]] || echo "$1" >"$FAKE_DIR/db_state"
  export FAKE_DIR
}

run_stop() { # run_stop <args...>; output in $FAKE_DIR/out, status in $STATUS
  # SC2086 on purpose: ${EXTRA_ENV:-} must vanish when empty, not become an empty argument for env.
  # shellcheck disable=SC2086
  env -i HOME="$HOME" PATH="$WORK/bin:$PATH" FAKE_DIR="$FAKE_DIR" \
    STOP_DATABASE_TIMEOUT_SECONDS=5 STOP_DATABASE_INTERVAL_SECONDS=1 \
    AZURE_RESOURCE_GROUP=rg-alivia AZURE_WEBAPP_NAME="${WEBAPP:-app-alivia-staging-abc12}" \
    AZURE_POSTGRES_SERVER_NAME="${SERVER:-psql-alivia-staging-abc12}" \
    ${EXTRA_ENV:-} \
    bash "$STOP" "$@" >"$FAKE_DIR/out" 2>&1
  STATUS=$?
}

contains_text() { grep -qF -- "$1" "$FAKE_DIR/out"; }
status_is() { [[ "$STATUS" == "$1" ]]; }
no_az_call() { ! grep -q -- "$1" "$FAKE_DIR/calls"; }
db_is() { [[ "$(cat "$FAKE_DIR/db_state")" == "$1" ]]; }
called_before() {
  local a b
  a="$(grep -n -m1 -- "$1" "$FAKE_DIR/calls" | cut -d: -f1)"
  b="$(grep -n -m1 -- "$2" "$FAKE_DIR/calls" | cut -d: -f1)"
  [[ -n "$a" && -n "$b" && "$a" -lt "$b" ]]
}

# --- cases ------------------------------------------------------------------------------------------------------------
echo "arguments"
new_case
run_stop
check "without arguments: usage error, status 2" status_is 2
run_stop qa
check "unknown environment: status 2" status_is 2
run_stop staging extra
check "an extra argument: status 2" status_is 2
check "no Azure call was made" test ! -s "$FAKE_DIR/calls"

echo "variables"
new_case
EXTRA_ENV="AZURE_POSTGRES_SERVER_NAME=" run_stop staging
check "a missing variable fails with status 1" status_is 1
check "the message names it" contains_text "faltan variables obligatorias: AZURE_POSTGRES_SERVER_NAME"
check "nothing was stopped" no_az_call " stop"

new_case
WEBAPP=app-alivia-production-abc12 run_stop staging
check "a Web App of another environment is refused" status_is 1
check "the message says so" contains_text "AZURE_WEBAPP_NAME no pertenece al entorno 'staging'"
check "nothing was stopped" no_az_call " stop"

new_case
SERVER=psql-alivia-production-abc12 run_stop staging
check "a server of another environment is refused" status_is 1
check "the message says so" contains_text "AZURE_POSTGRES_SERVER_NAME no pertenece al entorno 'staging'"
check "nothing was stopped" no_az_call " stop"

new_case
EXTRA_ENV="FAKE_NO_SESSION=1" run_stop staging
check "without an Azure session: fails" status_is 1
check "the message says so" contains_text "sesion iniciada"

echo "stopping"
new_case Ready
run_stop staging
check "status 0" status_is 0
check "the Web App was stopped" grep -q "webapp stop -g rg-alivia -n app-alivia-staging-abc12" "$FAKE_DIR/calls"
check "the server was stopped" grep -q "flexible-server stop -g rg-alivia -n psql-alivia-staging-abc12" "$FAKE_DIR/calls"
check "the Web App is stopped before the database" called_before "webapp stop" "flexible-server stop"
check "the server is Stopped" db_is Stopped

echo "running it twice"
run_stop staging
check "the second run also succeeds" status_is 0
check "and says the server was already stopped" contains_text "PostgreSQL ya estaba detenido"
check "the server was not stopped again" bash -c "[ \"\$(grep -c 'flexible-server stop' '$FAKE_DIR/calls')\" = 1 ]"

echo "server in transition"
new_case Starting
run_stop staging
check "Starting: waits, then stops it" bash -c "[ '$STATUS' = 0 ] && grep -q 'flexible-server stop' '$FAKE_DIR/calls'"

new_case Stopping
run_stop staging
check "Stopping: waits and does not stop it again" bash -c "[ '$STATUS' = 0 ] && ! grep -q 'flexible-server stop' '$FAKE_DIR/calls'"

echo "failures"
new_case Ready
EXTRA_ENV="FAKE_WEBAPP_STOP_FAILS=1" run_stop staging
check "a Web App that cannot be stopped fails" status_is 1
check "the message says so" contains_text "no se pudo detener la Web App"
check "the database is left alone" no_az_call "flexible-server"

new_case Ready
EXTRA_ENV="FAKE_DB_STOP_FAILS=1" run_stop staging
check "a server that cannot be stopped fails" status_is 1
check "the message says so" contains_text "no se pudo detener PostgreSQL"

new_case Disabled
run_stop staging
check "an unexpected state fails" status_is 1
check "the message names the state" contains_text "estado inesperado: 'Disabled'"

echo
echo "passed: $PASSED, failed: $FAILED"
[[ "$FAILED" -eq 0 ]]
