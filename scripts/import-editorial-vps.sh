#!/usr/bin/env bash
# Baja fotos libres, las deja en ~/ogt/media/u como un upload, borra posts y carga las 10 piezas.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${OGT_VPS_HOST:-iankmp-vps}"
REMOTE="${OGT_VPS_DIR:-ogt}"
UA="OnlyGoodThingsEditorial/1.0 (https://onlygoodthings.app; media import)"
STAGING="$ROOT/build/editorial-media"

mkdir -p "$STAGING"

fetch() {
  local dest="$1"
  local url="$2"
  echo "Bajando $(basename "$dest")…"
  curl -fsSL --max-time 40 -A "$UA" -o "$dest.tmp" "$url"
  if file "$dest.tmp" | grep -qiE 'JPEG|PNG|Web/P|WebP|image'; then
    mv "$dest.tmp" "$dest"
  else
    echo "No es imagen: $url" >&2
    file "$dest.tmp" >&2
    rm -f "$dest.tmp"
    exit 1
  fi
}

# Mismos UUID que 05_news_seed.sql → /media/u/{uuid}.jpg
fetch "$STAGING/c2000000-0000-4000-8000-000000000001.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/4/45/Giant_Tortoise.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000002.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c9/Lobos_marinos.jpg/960px-Lobos_marinos.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000003.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/98/Street_dog.jpg/960px-Street_dog.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000004.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/b/b3/Giant_armadillo.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000005.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/d/db/Nothofagus_dombeyi.jpg/960px-Nothofagus_dombeyi.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000006.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/d/da/Community_garden.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000007.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/6/6f/Reserva_Nacional_de_Paracas.jpg/960px-Reserva_Nacional_de_Paracas.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000008.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0c/Panthera_onca_at_the_Toronto_Zoo_2.jpg/960px-Panthera_onca_at_the_Toronto_Zoo_2.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000009.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e5/Green_turtle_swimming_over_coral_reefs_in_Kona.jpg/960px-Green_turtle_swimming_over_coral_reefs_in_Kona.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000010.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/7/79/Biblioteca.jpg"

echo "Subiendo archivos a $HOST:~/$REMOTE/media/u …"
ssh -o BatchMode=yes "$HOST" "mkdir -p ~/$REMOTE/media/u && docker exec ogt-api chmod 777 /app/media /app/media/u"
rsync -az "$STAGING/" "$HOST:~/$REMOTE/media/u/"

echo "Reemplazando posts en Postgres…"
{
  echo "BEGIN;"
  echo "DELETE FROM social_posts;"
  cat "$ROOT/database/migrations/05_news_seed.sql"
  echo "COMMIT;"
} | ssh -o BatchMode=yes "$HOST" "docker exec -i ogt-postgres psql -U ogt -d onlygoodthings"

echo "Posts:"
ssh -o BatchMode=yes "$HOST" 'docker exec ogt-postgres psql -U ogt -d onlygoodthings -c "SELECT count(*) AS posts FROM social_posts; SELECT topic, left(body,48) FROM social_posts ORDER BY created_at DESC;"'
echo "Archivos en media/u:"
ssh -o BatchMode=yes "$HOST" "ls -l ~/$REMOTE/media/u"
