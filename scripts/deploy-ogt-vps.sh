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
for path in web/app/crm/control/index.html web/app/crm/empresas/index.html backend/src/main/kotlin/com/onlygoodthings/backend/crm/CrmRoutes.kt database/migrations/23_crm_foundations.sql database/migrations/24_post_media_frame.sql database/migrations/25_post_media_original.sql database/migrations/26_crm_publication_index.sql database/migrations/28_crm_user_moderation.sql database/migrations/29_stories.sql database/migrations/30_story_frame.sql database/migrations/31_account_erasure.sql; do
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
SDK="${OGT_DB_KMP_SDK_PATH:-$HOME/AndroidStudioProjects/db-kmp-sdk}"
test -f "$SDK/settings.gradle.kts" || { echo 'Set OGT_DB_KMP_SDK_PATH to the existing private db-kmp-sdk checkout' >&2; exit 1; }
SDK_SHA="$(git -C "$SDK" rev-parse HEAD)"
test -z "$(git -C "$SDK" status --porcelain)" || { echo 'Commit the SDK changes before release' >&2; exit 1; }
HOST="${OGT_VPS_HOST:-iankmp-vps}"
[[ "$HOST" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ ]] || { echo 'Use a configured SSH alias, not a shell expression' >&2; exit 1; }
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15)
# Check server prerequisites BEFORE spending time on the build. These reads do not deploy.
"${SSH[@]}" "$HOST" 'test -f "$HOME/ogt/.env" && test -f "$HOME/ogt/docker-compose.yml"'
"${SSH[@]}" "$HOST" 'python3 - env "$HOME/ogt/.env"' < scripts/release_guard.py
# Gradle may be configured globally to cache on an unavailable external disk.
# Keep release dependencies in a persistent, private, writable LOCAL user cache.
# This does not remove, reset or migrate the original Gradle cache.
if [ "$(uname -s)" = Darwin ]; then
  DEFAULT_GRADLE_CACHE="$HOME/Library/Caches/OGT/gradle"
else
  DEFAULT_GRADLE_CACHE="$HOME/.cache/ogt-release-gradle"
fi
export GRADLE_USER_HOME="${OGT_RELEASE_GRADLE_HOME:-$DEFAULT_GRADLE_CACHE}"
( umask 077; mkdir -p "$GRADLE_USER_HOME" ) || { echo "No se puede crear la cache de Gradle de OGT: $GRADLE_USER_HOME" >&2; exit 1; }
test -d "$GRADLE_USER_HOME" && test -w "$GRADLE_USER_HOME" || { echo "La cache de Gradle de OGT no es escribible: $GRADLE_USER_HOME" >&2; exit 1; }
PROBE="$(mktemp "$GRADLE_USER_HOME/.ogt-write-test.XXXXXX")" || { echo "No se puede escribir en la cache de Gradle de OGT: $GRADLE_USER_HOME" >&2; exit 1; }
rm -f "$PROBE"
echo "Gradle OGT usa cache local: $GRADLE_USER_HOME"
# Compile fresh outputs OUTSIDE tracked web/app. syncWebSdk writes to tracked files
# and would make the subsequent source-integrity check reject a legitimate build.
./gradlew --gradle-user-home "$GRADLE_USER_HOME" "-Pogt.dbKmpSdkPath=$SDK" :backend:clean :shared:clean :backend:test :shared:jvmTest :backend:installDist :shared:jsBrowserProductionWebpack --no-daemon
# Building may create ignored output, but must not alter application or SDK source.
git diff --exit-code --quiet
git diff --cached --exit-code --quiet
git -C "$SDK" diff --exit-code --quiet
git -C "$SDK" diff --cached --exit-code --quiet
DIST="$ROOT/backend/build/install/backend"
BUNDLE="$ROOT/shared/build/kotlin-webpack/js/productionExecutable"
test -x "$DIST/bin/backend"
test -s "$BUNDLE/ogt-sdk.js"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/release/database/migrations" "$WORK/release/scripts"
cp -R "$DIST" "$WORK/release/backend-dist"
cp -R web/app "$WORK/release/web-app"
# Match syncWebSdk's artifact selection, but only in the temporary release package.
rm -f "$WORK/release/web-app/ogt-sdk.js" "$WORK/release/web-app/ogt-sdk.js.LICENSE.txt"
for artifact in "$BUNDLE"/*.js "$BUNDLE/ogt-sdk.js.LICENSE.txt"; do
  test -f "$artifact" || continue
  name="$(basename "$artifact")"
  case "$name" in app.js|auth.js|bootstrap.js) echo 'SDK artifact collides with application code' >&2; exit 1;; esac
  cp "$artifact" "$WORK/release/web-app/$name"
done
test -s "$WORK/release/web-app/ogt-sdk.js"
cp deploy/vps/Dockerfile deploy/vps/docker-compose.yml "$WORK/release/"
cp web/app/nginx.conf "$WORK/release/web-nginx.conf"
# Existing application schema is a prerequisite; never replay demo seed migrations.
cp database/migrations/23_crm_foundations.sql database/migrations/24_post_media_frame.sql database/migrations/25_post_media_original.sql database/migrations/26_crm_publication_index.sql database/migrations/28_crm_user_moderation.sql database/migrations/29_stories.sql database/migrations/30_story_frame.sql database/migrations/31_account_erasure.sql "$WORK/release/database/migrations/"
cp scripts/release_guard.py scripts/activate-ogt-release.sh "$WORK/release/scripts/"
printf '{"sdkCommit":"%s"}\n' "$SDK_SHA" > "$WORK/release/build-inputs.json"
python3 scripts/release_guard.py manifest "$WORK/release" "$SHA"
python3 scripts/release_guard.py verify "$WORK/release" "$SHA"
ID="$SHA-$(date -u +%Y%m%dT%H%M%SZ)"
"${SSH[@]}" "$HOST" "mkdir -p ogt/releases/$ID"
rsync -az -e 'ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15' "$WORK/release/" "$HOST:ogt/releases/$ID/"
"${SSH[@]}" "$HOST" "bash ogt/releases/$ID/scripts/activate-ogt-release.sh $ID $SHA"
echo 'Private first-version deployment finished. Public launch and real-account acceptance remain separate.'
echo 'OGT: https://onlygoodthings.lat/'
echo 'Control: https://onlygoodthings.lat/crm/control/'
echo 'Empresas: https://onlygoodthings.lat/crm/empresas/'
