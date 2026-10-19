#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# Isolated upstream: no published ports, mounts, production data or network calls.
docker run --rm -i --add-host api:127.0.0.1 nginx:alpine sh -ec '
  cat > /etc/nginx/conf.d/default.conf
  printf "server { listen 8080; return 200 \"qa-upstream\"; }\n" > /etc/nginx/conf.d/qa-upstream.conf
  nginx -t
  nginx
  for path in /media/u/photo.jpg /media/u/photo.webp /media/u/movie.mp4 /api/v1/test /health; do
    result=$(wget -qO- "http://127.0.0.1$path")
    test "$result" = qa-upstream
    echo "$path OK"
  done
' < web/app/nginx.conf
