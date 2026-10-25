#!/usr/bin/env bash
# Borra solo las copias ogt-* que quedaron en /Volumes/DEV_MAC.
# No toca /Volumes/DEV_MAC/OGT ni ~/AndroidStudioProjects/db-kmp-sdk.
set -euo pipefail

BASE=/Volumes/DEV_MAC
PATHS=(
  "$BASE/ogt-deploy-d53e68c"
  "$BASE/ogt-deploy-main"
  "$BASE/ogt-device-notes"
  "$BASE/ogt-public-store-0d7d"
  "$BASE/ogt-sdk-committed-6a02167"
  "$BASE/ogt-sparse"
  "$BASE/ogt-testflight-0d7d"
)

if [[ "${1:-}" != "--yes" ]]; then
  echo "Sin --yes solo lista. Para borrar: bash scripts/remove-ogt-extra-folders.sh --yes" >&2
  DRY=1
else
  DRY=0
fi

for path in "${PATHS[@]}"; do
  case "$path" in
    "$BASE/OGT"|"$BASE/OnlyGoodThings"|"$HOME/AndroidStudioProjects/db-kmp-sdk")
      echo "Ruta protegida: $path" >&2
      exit 1
      ;;
    "$BASE"/ogt-*) ;;
    *)
      echo "Ruta fuera de la lista: $path" >&2
      exit 1
      ;;
  esac
done

for path in "${PATHS[@]}"; do
  if [[ ! -e "$path" ]]; then
    echo "no está: $path"
    continue
  fi
  if [[ -L "$path" ]]; then
    echo "Es un enlace. No lo borro: $path" >&2
    exit 1
  fi
  echo "---- $path"
  if [[ -e "$path/.git" ]]; then
    git -C "$path" status -sb || true
    git -C "$path" rev-parse --short HEAD || true
  fi
  if [[ "$DRY" == 1 ]]; then
    continue
  fi
  if [[ -f "$path/.git" ]] && git -C "$path" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    common="$(git -C "$path" rev-parse --git-common-dir)"
    common="$(cd "$path" && cd "$common" && pwd)"
    git --git-dir="$common" worktree remove --force "$path"
  else
    rm -rf -- "$path"
  fi
  if [[ -e "$path" ]]; then
    echo "Sigue en disco: $path" >&2
    exit 1
  fi
  echo "eliminada: $path"
done

if [[ "$DRY" == 0 ]]; then
  if [[ -e "$BASE/OGT/.git" ]]; then
    git -C "$BASE/OGT" worktree prune || true
  fi
  sdk="$HOME/AndroidStudioProjects/db-kmp-sdk"
  if [[ -e "$sdk/.git" ]]; then
    git -C "$sdk" worktree prune || true
  fi
  echo "Listo. OGT y el SDK de AndroidStudioProjects quedaron."
fi
