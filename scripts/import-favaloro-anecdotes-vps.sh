#!/usr/bin/env bash
# Anécdotas del homenaje a Favaloro. No borra posts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${OGT_VPS_HOST:-iankmp-vps}"

{
  echo "BEGIN;"
  cat "$ROOT/database/migrations/16_favaloro_anecdotes.sql"
  echo "COMMIT;"
} | ssh -o BatchMode=yes "$HOST" "docker exec -i ogt-postgres psql -U ogt -d onlygoodthings"

ssh -o BatchMode=yes "$HOST" 'docker exec ogt-postgres psql -U ogt -d onlygoodthings -c "
  SELECT honoree_name, topic FROM social_posts WHERE id = '\''a2000000-0000-4000-8000-000000000011'\'';
  SELECT count(*) AS anecdotas FROM post_anecdotes WHERE post_id = '\''a2000000-0000-4000-8000-000000000011'\'';
"'
