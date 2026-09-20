#!/usr/bin/env bash
# Fotos Wikimedia Commons por tipo de mascota, para historias sin foto de nota.
set -u
DEST="/Users/romancanoniero/.cursor/OnlyGoodThings/composeApp/src/commonMain/composeResources/drawable"
FALLBACK="$DEST/feed_story_patitas.jpg"
mkdir -p "$DEST"

fetch() {
  local out="$1"
  local url="$2"
  if [ -z "$url" ]; then
    echo "KEEP_FALLBACK $out"
    return 1
  fi
  if curl -fsSL --max-time 25 -A "OnlyGoodThingsSeed/1.0 (pet stories; contact ogt)" -o "$out.tmp" "$url"; then
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

fetch_search() {
  local out="$1"
  local query="$2"
  local encoded json url
  if [ ! -f "$out" ]; then
    cp "$FALLBACK" "$out"
  fi
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
  sleep 2
}

fetch_search "$DEST/seed_pet_dog.jpg" "mixed breed dog portrait"
fetch_search "$DEST/seed_pet_puppy.jpg" "puppy sitting grass"
fetch_search "$DEST/seed_pet_cat.jpg" "domestic cat portrait"
fetch_search "$DEST/seed_pet_kitten.jpg" "orange kitten"
fetch_search "$DEST/seed_pet_rabbit.jpg" "pet rabbit"
fetch_search "$DEST/seed_pet_hamster.jpg" "syrian hamster"
fetch_search "$DEST/seed_pet_guinea.jpg" "guinea pig"
fetch_search "$DEST/seed_pet_parrot.jpg" "budgerigar parrot"
fetch_search "$DEST/seed_pet_turtle.jpg" "red-eared slider turtle"
fetch_search "$DEST/seed_pet_horse.jpg" "horse portrait"
fetch_search "$DEST/seed_pet_donkey.jpg" "donkey portrait"
fetch_search "$DEST/seed_pet_goat.jpg" "pygmy goat"
fetch_search "$DEST/seed_pet_duck.jpg" "domestic duck"
fetch_search "$DEST/seed_pet_hedgehog.jpg" "european hedgehog"
fetch_search "$DEST/seed_pet_ferret.jpg" "ferret pet"
fetch_search "$DEST/seed_pet_chinchilla.jpg" "chinchilla lanigera"
fetch_search "$DEST/seed_pet_pig.jpg" "pot-bellied pig"
fetch_search "$DEST/seed_pet_capybara.jpg" "capybara"
fetch_search "$DEST/seed_pet_golden.jpg" "golden retriever"
fetch_search "$DEST/seed_pet_tabby.jpg" "tabby cat"

echo DONE
ls "$DEST"/seed_pet_*.jpg | wc -l
