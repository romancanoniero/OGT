#!/usr/bin/env bash
# One existing installation: app, backend and both CRMs. Never a static-only release.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
MODE="${1:---check}"
case "$MODE" in --check|--deploy) ;; *) echo 'Use --check or --deploy' >&2; exit 2;; esac
REMOTE_URL="$(git remote get-url origin)"
case "$REMOTE_URL" in https://github.com/romancanoniero/OGT|https://github.com/romancanoniero/OGT.git|git@github.com:romancanoniero/OGT.git) ;; *) echo 'Wrong repository' >&2; exit 1;; esac
SHA="$(git rev-parse HEAD)"
test -z "$(git status --porcelain --untracked-files=normal)" || { echo 'Commit or remove local changes before building a release' >&2; exit 1; }
for tool in node python3 git; do command -v "$tool" >/dev/null; done
for path in web/app/crm/control/index.html web/app/crm/empresas/index.html backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt database/migrations/23_crm_foundations.sql; do
  test -s "$path" || { echo "Missing integrated component: $path" >&2; exit 1; }
done
node --check web/app/app.js
node --check web/app/auth.js
find web/app/crm -type f -name '*.js' -print0 | xargs -0 -n 1 node --check
node --test qa/auth-unit.test.mjs qa/crm-*.test.mjs
python3 -m unittest discover -s qa -p 'test_release*.py' -v
if [ "$MODE" = --check ]; then
  echo 'Source checks passed. No build, SSH connection or deployment was performed.'
  exit 0
fi
for tool in java rsync ssh tar; do command -v "$tool" >/dev/null; done
SDK="${OGT_DB_KMP_SDK_PATH:-/Users/romancanoniero/AndroidStudioProjects/db-kmp-sdk}"
test -f "$SDK/settings.gradle.kts" || { echo 'Set OGT_DB_KMP_SDK_PATH to the existing private db-kmp-sdk checkout' >&2; exit 1; }
SDK_SHA="$(git -C "$SDK" rev-parse HEAD)"
test -z "$(git -C "$SDK" status --porcelain)" || { echo 'Commit the SDK changes before release' >&2; exit 1; }
# No -x test and no reuse of an unverified static bundle.
./gradlew "-Pogt.dbKmpSdkPath=$SDK" :backend:test :shared:jvmTest :backend:installDist :shared:syncWebSdk --no-daemon
# Building may create ignored SDK output, but must not alter tracked source.
git diff --exit-code --quiet
git diff --cached --exit-code --quiet
DIST="$ROOT/backend/build/install/backend"
test -x "$DIST/bin/backend"
test -s web/app/ogt-sdk.js
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/release/database/migrations" "$WORK/release/scripts"
cp -R "$DIST" "$WORK/release/backend-dist"
cp -R web/app "$WORK/release/web-app"
cp deploy/vps/Dockerfile deploy/vps/docker-compose.yml "$WORK/release/"
cp web/app/nginx.conf "$WORK/release/web-nginx.conf"
# Existing application schema is a prerequisite; never replay demo seed migrations.
cp database/migrations/23_crm_foundations.sql "$WORK/release/database/migrations/"
cp scripts/release_guard.py scripts/activate-ogt-release.sh "$WORK/release/scripts/"
printf '{"sdkCommit":"%s"}\n' "$SDK_SHA" > "$WORK/release/build-inputs.json"
python3 scripts/release_guard.py manifest "$WORK/release" "$SHA"
python3 scripts/release_guard.py verify "$WORK/release" "$SHA"
HOST="${OGT_VPS_HOST:-iankmp-vps}"
[[ "$HOST" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ ]] || { echo 'Use a configured SSH alias, not a shell expression' >&2; exit 1; }
ID="$SHA-$(date -u +%Y%m%dT%H%M%SZ)"
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15)
# First check is read-only. The existing installation and credentials must exist.
"${SSH[@]}" "$HOST" 'test -f "$HOME/ogt/.env" && test -f "$HOME/ogt/docker-compose.yml"'
"${SSH[@]}" "$HOST" "mkdir -p ogt/releases/$ID"
rsync -az -e 'ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15' "$WORK/release/" "$HOST:ogt/releases/$ID/"
"${SSH[@]}" "$HOST" "bash ogt/releases/$ID/scripts/activate-ogt-release.sh $ID $SHA"
echo 'Private first-version deployment finished. Public launch and real-account acceptance remain separate.'
echo 'OGT: https://onlygoodthings.lat/'
echo 'Control: https://onlygoodthings.lat/crm/control/'
echo 'Empresas: https://onlygoodthings.lat/crm/empresas/'
