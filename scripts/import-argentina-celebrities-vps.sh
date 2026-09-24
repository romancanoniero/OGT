#!/usr/bin/env bash
# Baja retratos de Commons (licencia libre), los deja en ~/ogt/media/u y suma 20 posts.
# No borra las piezas editoriales ya cargadas.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${OGT_VPS_HOST:-iankmp-vps}"
REMOTE="${OGT_VPS_DIR:-ogt}"
UA="OnlyGoodThingsEditorial/1.0 (https://onlygoodthings.app; media import)"
STAGING="$ROOT/build/argentina-celebrities-media"

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

fetch "$STAGING/c2000000-0000-4000-8000-000000000011.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/f/f5/Rene_Favaloro.JPG/960px-Rene_Favaloro.JPG"
fetch "$STAGING/c2000000-0000-4000-8000-000000000012.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/8/87/Manuel_Belgrano.JPG/960px-Manuel_Belgrano.JPG"
fetch "$STAGING/c2000000-0000-4000-8000-000000000013.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/4/43/Estela_de_Carlotto.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000014.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/0/0f/Jose_de_San_Martin.jpg/960px-Jose_de_San_Martin.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000015.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/c/c1/Lionel_Messi_20180626.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000016.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/4/42/Adolfo_P%C3%A9rez_Esquivel_1983.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000017.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/6/62/Jorge_Mario_Bergoglio.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000018.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/d/de/Eva_Per%C3%B3n.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000019.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/d/de/Domingo_Faustino_Sarmiento.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000020.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/3/3d/Argentine_President_Raul_Alfonsin_says_good-bye_to_US_State_Department_officials_before_leaving_the_country.jpg/960px-Argentine_President_Raul_Alfonsin_says_good-bye_to_US_State_Department_officials_before_leaving_the_country.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000021.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a3/Mercedes_Sosa.jpg/960px-Mercedes_Sosa.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000022.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/b/b1/Manu_Gin%C3%B3bili.jpg/960px-Manu_Gin%C3%B3bili.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000023.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/2/2a/Carlos_Saavedra_Lamas.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000024.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/4/44/Cecilia_Grierson.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000025.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/9/93/Quino_%2813331254273%29_%28cropped%29.jpg/960px-Quino_%2813331254273%29_%28cropped%29.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000026.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/5d/Dr._Bernardo_Houssay_-_NLM_101442347.jpg/960px-Dr._Bernardo_Houssay_-_NLM_101442347.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000027.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/5/5e/Mart%C3%ADn_Miguel_de_G%C3%BCemes.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000028.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/2/23/Juan_Mart%C3%ADn_del_Potro.jpg/960px-Juan_Mart%C3%ADn_del_Potro.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000029.jpg" \
  "https://upload.wikimedia.org/wikipedia/commons/e/e7/Carlos_Mugica.jpg"
fetch "$STAGING/c2000000-0000-4000-8000-000000000030.jpg" \
  "https://thumb.wikimedia.org/wikipedia/commons/thumb/5/52/Juana_Azurduy.jpg/960px-Juana_Azurduy.jpg"

echo "Subiendo archivos a $HOST:~/$REMOTE/media/u …"
ssh -o BatchMode=yes "$HOST" "mkdir -p ~/$REMOTE/media/u && docker exec ogt-api chmod 777 /app/media /app/media/u"
rsync -az --omit-dir-times --no-perms --no-owner --no-group "$STAGING/" "$HOST:~/$REMOTE/media/u/"

echo "Insertando 20 posts (sin borrar los existentes)…"
{
  echo "BEGIN;"
  cat "$ROOT/database/migrations/12_argentina_celebrities.sql"
  echo "COMMIT;"
} | ssh -o BatchMode=yes "$HOST" "docker exec -i ogt-postgres psql -U ogt -d onlygoodthings"

echo "Posts:"
ssh -o BatchMode=yes "$HOST" 'docker exec ogt-postgres psql -U ogt -d onlygoodthings -c "SELECT count(*) AS posts FROM social_posts; SELECT topic, left(body,56) FROM social_posts ORDER BY created_at DESC;"'
echo "Archivos nuevos en media/u:"
ssh -o BatchMode=yes "$HOST" "ls -l ~/$REMOTE/media/u/c2000000-0000-4000-8000-00000000001{1,2,3,4,5,6,7,8,9}* ~/$REMOTE/media/u/c2000000-0000-4000-8000-00000000002{0,1,2,3,4,5,6,7,8,9}* ~/$REMOTE/media/u/c2000000-0000-4000-8000-000000000030.jpg"
