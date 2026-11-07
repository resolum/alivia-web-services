#!/usr/bin/env bash
#
# Tests of deploy.sh with a fake `az` and a fake `curl`: nothing here touches Azure or the network.
#
#   infrastructure/scripts/tests/deploy_test.sh
#
# What they cannot show: that the real `az` accepts these arguments, that the roles of the deployment identity are
# enough, or how a real App Service behaves. That is checked by running deploy.sh by hand against staging.

set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY="$HERE/../deploy.sh"
PYTHON="$(command -v python3 || command -v python || true)"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --- fake az ----------------------------------------------------------------------------------------------------------
# State lives in $FAKE_DIR: image (what runs now), tags (images in the registry), calls (every az call), settings.json.
mkdir -p "$WORK/bin"
cat >"$WORK/bin/az" <<'FAKE_AZ'
#!/usr/bin/env bash
echo "az $*" >>"$FAKE_DIR/calls"
args="$*"
case "$args" in
  "account show"*) exit 0 ;;
  "acr repository show"*)
    tag="${args##*--image alivia-api:}"
    tag="${tag%% *}"
    grep -qx "$tag" "$FAKE_DIR/tags" ;;
  "webapp config show"*) cat "$FAKE_DIR/image" ;;
  "webapp config set"*)
    image="${args##*--linux-fx-version DOCKER|}"
    printf 'DOCKER|%s' "${image%% *}" >"$FAKE_DIR/image" ;;
  "webapp config appsettings set"*)
    file="${args##*--settings @}"
    cp "${file%% *}" "$FAKE_DIR/settings.json" ;;
  "webapp show"*) echo "fake-app.example.net" ;;
  "webapp restart"*) exit 0 ;;
  *) echo "fake az: unexpected call: $args" >&2; exit 99 ;;
esac
FAKE_AZ

# --- fake curl --------------------------------------------------------------------------------------------------------
# The application answers UP unless the running tag is listed in $FAKE_DIR/broken.
cat >"$WORK/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
url="${*: -1}"
path="/${url#https://fake-app.example.net/}"
running="$(cat "$FAKE_DIR/image")"
tag="${running##*:}"
broken=0
grep -qx "$tag" "$FAKE_DIR/broken" 2>/dev/null && broken=1
if [[ "$*" == *"-w"* ]]; then
  case "$path" in
    /actuator/info | /api/v1/plans) code=200 ;;
    /v3/api-docs) code="${FAKE_API_DOCS_STATUS:-404}" ;;
    /api/v1/devices) code=401 ;;
    *) code=404 ;;
  esac
  ((broken)) && code=503
  printf '%s' "$code"
  exit 0
fi
if ((broken)); then exit 22; fi
printf '{"status":"UP"}'
FAKE_CURL
chmod +x "$WORK/bin/az" "$WORK/bin/curl"

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

SECRET_PASSWORD='p"a\ss'"'"'w0rd'$'\t''end'

new_case() { # new_case <image-now> <tags-in-registry...>
  FAKE_DIR="$WORK/case.$RANDOM"
  mkdir -p "$FAKE_DIR/tmp"
  printf 'DOCKER|%s' "$1" >"$FAKE_DIR/image"
  shift
  printf '%s\n' "$@" >"$FAKE_DIR/tags"
  : >"$FAKE_DIR/calls"
  export FAKE_DIR
}

run_deploy() { # run_deploy <args...>; output in $FAKE_DIR/out, status in $STATUS
  env -i HOME="$HOME" PATH="$WORK/bin:$PATH" TMPDIR="$FAKE_DIR/tmp" FAKE_DIR="$FAKE_DIR" \
    DEPLOY_WARMUP_SECONDS=0 DEPLOY_HEALTH_TIMEOUT_SECONDS=2 DEPLOY_HEALTH_INTERVAL_SECONDS=1 \
    AZURE_RESOURCE_GROUP=rg-alivia AZURE_WEBAPP_NAME="${WEBAPP:-app-alivia-staging-abc12}" ACR_NAME=acrtest \
    DB_URL='jdbc:postgresql://db:5432/alivia?sslmode=require' DB_USERNAME=alivia_admin DB_PASSWORD="$SECRET_PASSWORD" \
    MONGODB_URI='mongodb+srv://u:mongo-secret-value@cluster.example.net/db' MONGODB_DATABASE=alivia_db \
    IAM_JWT_SECRET='jwt-secret-value-of-more-than-32-bytes!!' \
    CORS_ALLOWED_ORIGINS=https://front.example.net IAM_EMAIL_VERIFICATION_LINK_BASE_URL=https://front.example.net/verify \
    STRIPE_SECRET_KEY=sk_test_x STRIPE_PUBLISHABLE_KEY=pk_test_x STRIPE_WEBHOOK_SECRET=whsec_x \
    CLOUDINARY_CLOUD_NAME=cloud CLOUDINARY_API_KEY=cld-key-value CLOUDINARY_API_SECRET=cld-secret-value \
    IAM_SUPER_ADMIN_EMAIL=admin@example.net IAM_SUPER_ADMIN_PASSWORD='admin-pass-value' \
    ${EXTRA_ENV:-} \
    bash "$DEPLOY" "$@" >"$FAKE_DIR/out" 2>&1
  STATUS=$?
}

contains_text() { grep -qF -- "$1" "$FAKE_DIR/out"; }
lacks_text() { ! grep -qF -- "$1" "$FAKE_DIR/out"; }
image_is() { [[ "$(cat "$FAKE_DIR/image")" == "DOCKER|$1" ]]; }
status_is() { [[ "$STATUS" == "$1" ]]; }
no_az_call() { ! grep -q -- "$1" "$FAKE_DIR/calls"; }
no_temp_files() { [[ -z "$(ls -A "$FAKE_DIR/tmp")" ]]; }

# --- cases ------------------------------------------------------------------------------------------------------------
echo "arguments"
new_case acrtest.azurecr.io/alivia-api:old old
run_deploy
check "without arguments: usage error, status 2" status_is 2
run_deploy qa abc123
check "unknown environment: status 2" status_is 2
run_deploy staging latest
check "tag 'latest' is refused: status 2" status_is 2
run_deploy staging 'bad tag'
check "tag with a space is refused: status 2" status_is 2
check "no Azure call was made" test ! -s "$FAKE_DIR/calls"

echo "variables"
new_case acrtest.azurecr.io/alivia-api:old old new
EXTRA_ENV="DB_PASSWORD=" run_deploy staging new
check "a missing variable fails with status 1" status_is 1
check "the message names the variable" contains_text "faltan variables obligatorias: DB_PASSWORD"
check "nothing was changed" no_az_call "webapp config"

new_case acrtest.azurecr.io/alivia-api:old old new
WEBAPP=app-alivia-production-abc12 run_deploy staging new
check "a Web App of another environment is refused" status_is 1
check "the message says so" contains_text "no pertenece al entorno 'staging'"

new_case acrtest.azurecr.io/alivia-api:old old new
EXTRA_ENV="IAM_JWT_SECRET=short" run_deploy staging new
check "a short JWT secret is refused" status_is 1

new_case acrtest.azurecr.io/alivia-api:old old new
EXTRA_ENV="IAM_SUPER_ADMIN_PASSWORD=PASSWORD" run_deploy staging new
check "the placeholder super-admin password is refused" status_is 1

echo "image missing from the registry"
new_case acrtest.azurecr.io/alivia-api:old old
run_deploy staging new
check "fails with status 1" status_is 1
check "settings were not applied" no_az_call "appsettings set"
check "the image was not changed" image_is acrtest.azurecr.io/alivia-api:old

echo "successful deployment"
new_case acrtest.azurecr.io/alivia-api:old old new
run_deploy staging new
check "status 0" status_is 0
check "the new image runs" image_is acrtest.azurecr.io/alivia-api:new
check "prints the result line" contains_text "Desplegado new en staging: https://fake-app.example.net"
check "no secret value reaches the output" bash -c "! grep -qE 'mongo-secret-value|jwt-secret-value|cld-secret-value|admin-pass-value|sk_test_x' '$FAKE_DIR/out'"
check "the password with quotes is not printed" lacks_text 'p"a'
check "the settings file is removed" no_temp_files
if [[ -n "$PYTHON" ]]; then
  json_check() {
    "$PYTHON" -I - "$FAKE_DIR/settings.json" "$SECRET_PASSWORD" <<'PY'
import json, sys
settings = {s["name"]: s["value"] for s in json.load(open(sys.argv[1]))}
assert settings["DB_PASSWORD"] == sys.argv[2], "DB_PASSWORD did not survive the JSON escaping"
assert settings["PORT"] == "8080" and settings["WEBSITES_PORT"] == "8080"
assert settings["SPRING_PROFILES_ACTIVE"] == "prod"
assert settings["MANAGEMENT_HEALTH_MONGODB_ENABLED"] == "false"
assert settings["SERVER_FORWARD_HEADERS_STRATEGY"] == "framework"
assert "IAM_EMAIL_PROVIDER" not in settings, "an unset optional variable must not be copied"
PY
  }
  check "settings: valid JSON, special characters intact, fixed values present" json_check
else
  echo "  skip JSON checks: no python found"
fi
check "az output is silenced for the settings call" grep -q "appsettings set.*-o none" "$FAKE_DIR/calls"

echo "unhealthy deployment with a previous image"
new_case acrtest.azurecr.io/alivia-api:old old new
echo new >"$FAKE_DIR/broken"
run_deploy staging new
check "status 1" status_is 1
check "the previous image is back" image_is acrtest.azurecr.io/alivia-api:old
check "says it was restored" contains_text "Imagen anterior restaurada y en UP"
check "the settings file is removed" no_temp_files

echo "unhealthy deployment on the first deployment (nothing to go back to)"
new_case acrtest.azurecr.io/alivia-api:initial new
echo new >"$FAKE_DIR/broken"
run_deploy staging new
check "status 1" status_is 1
check "no restore is attempted" contains_text "no se puede restaurar"
check "the failed image is left in place" image_is acrtest.azurecr.io/alivia-api:new

echo "swagger exposed"
new_case acrtest.azurecr.io/alivia-api:old old new
EXTRA_ENV="FAKE_API_DOCS_STATUS=200" run_deploy staging new
check "a public /v3/api-docs fails the deployment" status_is 1
check "and rolls back" image_is acrtest.azurecr.io/alivia-api:old

echo
echo "passed: $PASSED, failed: $FAILED"
[[ "$FAILED" -eq 0 ]]
