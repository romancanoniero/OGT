#!/usr/bin/env bash
# Compila el backend en esta máquina y lo levanta en la VPS (~/ogt) con Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${OGT_VPS_HOST:-iankmp-vps}"
REMOTE="${OGT_VPS_DIR:-ogt}"
PORT="${OGT_VPS_PORT:-19080}"

cd "$ROOT"
echo "Compilando :backend:installDist…"
./gradlew :backend:installDist --quiet

DIST="$ROOT/backend/build/install/backend"
if [[ ! -x "$DIST/bin/backend" ]]; then
  echo "No está el dist de backend en $DIST" >&2
  exit 1
fi

ssh -o BatchMode=yes "$HOST" "mkdir -p ~/$REMOTE/database"
rsync -az --delete "$DIST/" "$HOST:~/$REMOTE/backend-dist/"
rsync -az --delete "$ROOT/database/migrations/" "$HOST:~/$REMOTE/database/migrations/"
rsync -az "$ROOT/deploy/vps/Dockerfile" "$ROOT/deploy/vps/docker-compose.yml" "$HOST:~/$REMOTE/"

ssh -o BatchMode=yes "$HOST" bash -s -- "$REMOTE" "$PORT" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
PORT="$2"
cd "$HOME/$REMOTE_DIR"
if [[ ! -f .env ]]; then
  PASS="$(openssl rand -hex 18)"
  umask 077
  cat > .env <<EOF
OGT_DB_USER=ogt
OGT_DB_PASSWORD=$PASS
OGT_ALLOW_DEV_TOKENS=true
OGT_FIREBASE_PROJECT_ID=goodthings-55612
OGT_FIREBASE_CREDENTIALS=
EOF
  echo "Creé ~/${REMOTE_DIR}/.env (no se sube al repo)."
fi
docker compose up -d --build
echo "Esperando /health…"
for i in $(seq 1 40); do
  if curl -fsS "http://127.0.0.1:${PORT}/health" >/dev/null 2>&1; then
    curl -sS "http://127.0.0.1:${PORT}/health"
    echo
    exit 0
  fi
  sleep 2
done
echo "El API no respondió en :${PORT}" >&2
docker compose logs --tail=80 api
exit 1
REMOTE

echo "Backend OGT: http://217.216.82.209:${PORT}/health"
