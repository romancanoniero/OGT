#!/usr/bin/env bash
# Compila el backend en esta máquina y lo levanta en la VPS (~/ogt) con Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${OGT_VPS_HOST:-iankmp-vps}"
REMOTE="${OGT_VPS_DIR:-ogt}"
PORT="${OGT_VPS_PORT:-19080}"

cd "$ROOT"
echo "Compilando :backend:installDist y el SDK web…"
./gradlew :backend:installDist :shared:syncWebSdk --quiet

DIST="$ROOT/backend/build/install/backend"
if [[ ! -x "$DIST/bin/backend" ]]; then
  echo "No está el dist de backend en $DIST" >&2
  exit 1
fi

ssh -o BatchMode=yes "$HOST" "mkdir -p ~/$REMOTE/database"
rsync -az --delete "$DIST/" "$HOST:~/$REMOTE/backend-dist/"
rsync -az --delete "$ROOT/database/migrations/" "$HOST:~/$REMOTE/database/migrations/"
rsync -az --delete "$ROOT/web/app/" "$HOST:~/$REMOTE/web-app/"
rsync -az "$ROOT/web/app/nginx.conf" "$HOST:~/$REMOTE/web-nginx.conf"
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
# nginx no recarga /db si solo cambió el bind-mount: recrear el contenedor web.
docker compose up -d --force-recreate --no-deps web
echo "Aplicando migraciones incrementales…"
for f in database/migrations/17_web_community.sql database/migrations/18_chat_mensaje.sql; do
  if [[ -f "$f" ]]; then
    docker exec -i ogt-postgres psql -U ogt -d onlygoodthings < "$f"
  fi
done
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
echo "Web OGT:     https://onlygoodthings.lat/"
echo "Sondeando wss://onlygoodthings.lat/db (corta sola, sin curl -N)…"
python3 "$ROOT/scripts/probe-ogt-db.py"
