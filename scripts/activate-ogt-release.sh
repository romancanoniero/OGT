#!/usr/bin/env bash
# Runs on the existing OGT VPS. Does not create databases, demo users or privileges.
set -euo pipefail
umask 077
ID="${1:?release id required}"; SHA="${2:?commit required}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ && "$ID" =~ ^[0-9a-f]{40}-[0-9]{8}T[0-9]{6}Z$ && "$ID" == "$SHA-"* ]] || exit 2
ROOT="$HOME/ogt"; RELEASE="$ROOT/releases/$ID"
cd "$ROOT"
exec 9>"$ROOT/.release.lock"
flock -n 9 || { echo 'Another OGT release is running' >&2; exit 1; }
python3 "$RELEASE/scripts/release_guard.py" verify "$RELEASE" "$SHA"
python3 "$RELEASE/scripts/release_guard.py" env "$ROOT/.env"
for name in ogt-postgres ogt-redis ogt-api ogt-web; do
  test "$(docker inspect -f '{{.State.Running}}' "$name")" = true || { echo "Existing service not running: $name" >&2; exit 1; }
done
# The pre-existing web schema must already be installed. Fail instead of guessing history.
for relation in users companies company_admins campaigns content_reports social_posts notice_reads timebank_needs; do
  test "$(docker exec ogt-postgres psql -X -U ogt -d onlygoodthings -Atc "SELECT to_regclass('public.$relation') IS NOT NULL")" = t || { echo "Missing prerequisite table: $relation" >&2; exit 1; }
done
for file in docker-compose.yml Dockerfile web-nginx.conf; do test -s "$ROOT/$file"; done
test -d web-app && test -d backend-dist
# Validate candidate routing before interrupting any running service.
docker run --rm --network ogt -v "$RELEASE/web-nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.28-alpine nginx -t
docker build -t "ogt-backend:$ID" "$RELEASE"
docker compose --project-directory "$ROOT" --env-file "$ROOT/.env" -f "$RELEASE/docker-compose.yml" config --quiet
BACKUP="$ROOT/backups/$ID"
mkdir -p "$BACKUP"; chmod 700 "$BACKUP"
tar -czf "$BACKUP/application.tgz" web-app backend-dist docker-compose.yml Dockerfile web-nginx.conf
OLD_IMAGE="$(docker inspect -f '{{.Image}}' ogt-api)"
docker tag "$OLD_IMAGE" "ogt-backend:before-$ID"
printf '%s\n' "$OLD_IMAGE" > "$BACKUP/previous-image.txt"
# From this point failures leave web/API stopped rather than expose mixed versions.
trap 'code=$?; if [ "$code" -ne 0 ]; then docker stop ogt-web ogt-api >/dev/null 2>&1 || true; echo "Release failed. OGT web/API remain stopped. Recovery material: $BACKUP" >&2; fi' EXIT
docker stop ogt-web ogt-api
docker exec ogt-postgres pg_dump -U ogt -d onlygoodthings -Fc > "$BACKUP/database.dump"
test -s "$BACKUP/database.dump"
docker exec -i ogt-postgres pg_restore --list < "$BACKUP/database.dump" > "$BACKUP/database-toc.txt"
test -s "$BACKUP/database-toc.txt"
# Only the additive CRM migration; never a wildcard over demo/seed SQL.
docker exec -i ogt-postgres psql -X -v ON_ERROR_STOP=1 --single-transaction -U ogt -d onlygoodthings < "$RELEASE/database/migrations/23_crm_foundations.sql"
rsync -a --delete "$RELEASE/backend-dist/" "$ROOT/backend-dist/"
rsync -a --delete "$RELEASE/web-app/" "$ROOT/web-app/"
cp "$RELEASE/docker-compose.yml" "$RELEASE/Dockerfile" "$RELEASE/web-nginx.conf" "$ROOT/"
docker tag "ogt-backend:$ID" ogt-backend:local
# Keep the existing PostgreSQL, Redis, media, .env and Firebase credentials.
docker compose up -d --no-deps --force-recreate api web
healthy=0
for i in $(seq 1 40); do
  if curl -fsS --max-time 5 http://127.0.0.1:19080/health >/dev/null; then healthy=1; break; fi
  sleep 2
done
test "$healthy" -eq 1
curl -fsS --max-time 15 http://127.0.0.1:19081/release.json > "$BACKUP/published-release.json"
python3 - "$BACKUP/published-release.json" "$SHA" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
assert r['sourceSha'] == sys.argv[2] and r['scope'] == 'app-backend-crms'
PY
for page in control empresas; do
  curl -fsS --max-time 15 "http://127.0.0.1:19081/crm/$page/" | grep -q 'id="crm-root"'
done
for path in access platform/summary company/summary; do
  code="$(curl -sS --max-time 15 -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d '{}' "http://127.0.0.1:19081/api/v1/crm/$path")"
  test "$code" = 401 || { echo "Unauthenticated CRM request returned $code" >&2; exit 1; }
done
# Both direct ports must be loopback-only; external clients must pass the review gate.
for spec in 'ogt-api 8080/tcp' 'ogt-web 80/tcp'; do
  set -- $spec
  test "$(docker port "$1" "$2" | cut -d: -f1 | sort -u)" = 127.0.0.1
done
test "$(docker inspect -f '{{index .Config.Labels "traefik.http.routers.ogt-web.middlewares"}}' ogt-web)" = ogt-prelaunch@docker
cp "$RELEASE/release-manifest.json" "$ROOT/current-release.json"
printf '%s\n' 'DEPLOYED_PRIVATE_CANDIDATE: local health, files and anonymous denials verified.'
printf '%s\n' 'Still required: HTTPS from an allowed network, live MFA, roles and operational acceptance.'
