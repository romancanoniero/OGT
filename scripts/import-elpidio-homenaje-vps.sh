#!/usr/bin/env bash
# Foto libre + homenaje de undercover a Don Elpidio. No borra posts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${OGT_VPS_HOST:-iankmp-vps}"
REMOTE="${OGT_VPS_DIR:-ogt}"
UA="OnlyGoodThingsEditorial/1.0 (https://onlygoodthings.app; media import)"
STAGING="$ROOT/build/elpidio-media"
DEST="$STAGING/c2000000-0000-4000-8000-000000000040.jpg"

mkdir -p "$STAGING"
echo "Bajando retrato…"
curl -fsSL --max-time 40 -A "$UA" -o "$DEST.tmp" \
  "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d0/Elpidio_Gonz%C3%A1lez.JPG/960px-Elpidio_Gonz%C3%A1lez.JPG"
if file "$DEST.tmp" | grep -qiE 'JPEG|PNG|image'; then
  mv "$DEST.tmp" "$DEST"
else
  echo "No es imagen" >&2
  file "$DEST.tmp" >&2
  rm -f "$DEST.tmp"
  exit 1
fi

echo "Subiendo foto…"
ssh -o BatchMode=yes "$HOST" "mkdir -p ~/$REMOTE/media/u"
rsync -az --omit-dir-times --no-perms --no-owner --no-group "$STAGING/" "$HOST:~/$REMOTE/media/u/"

echo "Aplicando esquema y homenaje…"
{
  echo "BEGIN;"
  cat "$ROOT/database/migrations/13_post_anecdotes.sql"
  cat "$ROOT/database/migrations/14_elpidio_homenaje.sql"
  echo "COMMIT;"
} | ssh -o BatchMode=yes "$HOST" "docker exec -i ogt-postgres psql -U ogt -d onlygoodthings"

ssh -o BatchMode=yes "$HOST" 'docker exec ogt-postgres psql -U ogt -d onlygoodthings -c "
  SELECT display_name, firebase_uid FROM users WHERE display_name = '\''undercover'\'';
  SELECT honoree_name, topic, left(body,48) FROM social_posts WHERE id = '\''a2000000-0000-4000-8000-000000000040'\'';
  SELECT count(*) AS anecdotas FROM post_anecdotes WHERE post_id = '\''a2000000-0000-4000-8000-000000000040'\'';
"'
