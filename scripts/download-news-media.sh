#!/usr/bin/env bash
# Baja fotos de Wikimedia Commons ligadas al tema de cada noticia.
# Si la API o el archivo fallan, queda el fallback para que Compose compile.
set -u
DEST="/Users/romancanoniero/.cursor/OnlyGoodThings/composeApp/src/commonMain/composeResources/drawable"
FALLBACKS=("$DEST/feed_photo_arboles.jpg" "$DEST/feed_photo_bebederos.jpg" "$DEST/feed_story_playa.jpg" "$DEST/feed_story_patitas.jpg" "$DEST/feed_story_compost.jpg" "$DEST/feed_story_donacion.jpg")

mkdir -p "$DEST"
for i in $(seq -w 1 40); do
  idx=$((10#$i % ${#FALLBACKS[@]}))
  if [ ! -f "$DEST/seed_n${i}.jpg" ]; then
    cp "${FALLBACKS[$idx]}" "$DEST/seed_n${i}.jpg"
  fi
done
for i in 01 02 03 07 10 11 15 20 23 28; do
  if [ ! -f "$DEST/seed_n${i}b.jpg" ]; then
    cp "$DEST/feed_story_playa.jpg" "$DEST/seed_n${i}b.jpg"
  fi
done

fetch() {
  local out="$1"
  local url="$2"
  if [ -z "$url" ]; then
    echo "KEEP_FALLBACK $out"
    return 1
  fi
  if curl -fsSL --max-time 25 -A "OnlyGoodThingsSeed/1.0 (news demo; contact ogt)" -o "$out.tmp" "$url"; then
    if file "$out.tmp" | grep -qiE 'image|jpeg|png|webp'; then
      mv "$out.tmp" "$out"
      echo "OK $out"
      return 0
    fi
  fi
  rm -f "$out.tmp"
  echo "KEEP_FALLBACK $out"
  return 1
}

# Busca un archivo en Commons y baja el thumb de 800px.
fetch_search() {
  local out="$1"
  local query="$2"
  local encoded json url
  encoded=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))" "$query")
  json=$(curl -fsSL --max-time 20 -A "OnlyGoodThingsSeed/1.0" \
    "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrsearch=${encoded}&gsrlimit=1&prop=imageinfo&iiprop=url&iiurlwidth=800&format=json") || {
    echo "KEEP_FALLBACK $out"
    return 1
  }
  url=$(printf '%s' "$json" | python3 -c "
import json,sys
d=json.load(sys.stdin)
pages=(d.get('query') or {}).get('pages') or {}
if not pages:
    raise SystemExit(1)
info=next(iter(pages.values())).get('imageinfo') or []
if not info:
    raise SystemExit(1)
print(info[0].get('thumburl') or info[0]['url'])
") || {
    echo "KEEP_FALLBACK $out"
    return 1
  }
  fetch "$out" "$url"
}

fetch_search "$DEST/seed_n01.jpg" "Galapagos giant tortoise"
fetch_search "$DEST/seed_n01b.jpg" "Chelonoidis nigra Santa Cruz"
fetch_search "$DEST/seed_n02.jpg" "South American sea lion"
fetch_search "$DEST/seed_n02b.jpg" "Otaria flavescens"
fetch_search "$DEST/seed_n03.jpg" "adopted rescue dog smiling"
fetch_search "$DEST/seed_n03b.jpg" "happy mixed breed dog"
fetch_search "$DEST/seed_n04.jpg" "street dogs Mexico City"
fetch_search "$DEST/seed_n05.jpg" "tree planting volunteers"
fetch_search "$DEST/seed_n06.jpg" "community garden fruit trees"
fetch_search "$DEST/seed_n07.jpg" "vegetable seedlings farm"
fetch_search "$DEST/seed_n07b.jpg" "volunteers planting seedlings"
fetch_search "$DEST/seed_n08.jpg" "food bank donation boxes"
fetch_search "$DEST/seed_n09.jpg" "fresh fruit harvest crates"
fetch_search "$DEST/seed_n10.jpg" "whale shark Rhincodon typus"
fetch_search "$DEST/seed_n10b.jpg" "whale shark underwater"
fetch_search "$DEST/seed_n11.jpg" "coral reef restoration"
fetch_search "$DEST/seed_n11b.jpg" "coral outplanting"
fetch_search "$DEST/seed_n12.jpg" "Bali coral reef"
fetch_search "$DEST/seed_n13.jpg" "school children lunch India"
fetch_search "$DEST/seed_n14.jpg" "community kitchen women cooking"
fetch_search "$DEST/seed_n15.jpg" "beach cleanup volunteers bags"
fetch_search "$DEST/seed_n15b.jpg" "plastic pollution beach cleanup"
fetch_search "$DEST/seed_n16.jpg" "community dining tables"
fetch_search "$DEST/seed_n17.jpg" "children sharing food merienda"
fetch_search "$DEST/seed_n18.jpg" "urban community garden Chile"
fetch_search "$DEST/seed_n19.jpg" "mangrove planting Caribbean"
fetch_search "$DEST/seed_n20.jpg" "mother dog and puppy rescue"
fetch_search "$DEST/seed_n20b.jpg" "rescue dog shelter"
fetch_search "$DEST/seed_n21.jpg" "sea turtle hatchling beach"
fetch_search "$DEST/seed_n22.jpg" "green sea turtle Chelonia mydas"
fetch_search "$DEST/seed_n23.jpg" "Amazon river turtle Podocnemis"
fetch_search "$DEST/seed_n23b.jpg" "Podocnemis unifilis"
fetch_search "$DEST/seed_n24.jpg" "loggerhead sea turtle Caretta"
fetch_search "$DEST/seed_n25.jpg" "community fridge free food"
fetch_search "$DEST/seed_n26.jpg" "dog adoption shelter portrait"
fetch_search "$DEST/seed_n27.jpg" "puppy adoption event"
fetch_search "$DEST/seed_n28.jpg" "shelter dogs waiting adoption"
fetch_search "$DEST/seed_n28b.jpg" "beagle rescue dog"
fetch_search "$DEST/seed_n29.jpg" "sea turtle release Mexico"
fetch_search "$DEST/seed_n30.jpg" "mangrove seedlings plastic cleanup"
fetch_search "$DEST/seed_n31.jpg" "tree planting Argentina volunteers"
fetch_search "$DEST/seed_n32.jpg" "children merienda community meal"
fetch_search "$DEST/seed_n33.jpg" "community kitchen volunteers cooking"
fetch_search "$DEST/seed_n34.jpg" "rescue dogs airplane transport"
fetch_search "$DEST/seed_n35.jpg" "oyster reef restoration volunteers"
fetch_search "$DEST/seed_n36.jpg" "international coastal cleanup beach"
fetch_search "$DEST/seed_n37.jpg" "school breakfast India children"
fetch_search "$DEST/seed_n38.jpg" "Hawaii beach cleanup bags"
fetch_search "$DEST/seed_n39.jpg" "scout volunteers park cleanup"
fetch_search "$DEST/seed_n40.jpg" "Biscayne National Park beach"

echo DONE
ls "$DEST"/seed_n*.jpg | wc -l
