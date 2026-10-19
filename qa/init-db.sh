#!/bin/bash
set -euo pipefail
for migration in /ogt-migrations/*.sql; do
  # Historical editorial import bound to an existing production identity, not schema.
  # Never invent that user's account in QA or pull production data to make it pass.
  case "$(basename "$migration")" in
    14_elpidio_homenaje.sql) echo 'QA: excluding production-specific editorial import 14'; continue ;;
  esac
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$migration"
done
