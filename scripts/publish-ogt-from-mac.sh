#!/usr/bin/env bash
# Run from a fresh OGT checkout. Reuse the Mac SSH identity, not GitHub secrets.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[[ "${1:-}" == --deploy ]] || { echo 'Uso: bash scripts/publish-ogt-from-mac.sh --deploy' >&2; exit 2; }
[[ "$(uname -s)" == Darwin ]] || { echo 'Este comando requiere la Mac con el acceso SSH de OGT.' >&2; exit 1; }
for tool in git node python3 java ssh rsync curl; do
  command -v "$tool" >/dev/null || { echo "Falta la herramienta local: $tool" >&2; exit 1; }
done
java -version >/dev/null 2>&1 || { echo 'No hay un Java operativo en esta Mac. Se requiere Java 17 o superior compatible con Gradle.' >&2; exit 1; }
export OGT_DB_KMP_SDK_PATH="${OGT_DB_KMP_SDK_PATH:-$HOME/AndroidStudioProjects/db-kmp-sdk}"
SDK="$OGT_DB_KMP_SDK_PATH"
test -f "$SDK/settings.gradle.kts" || { echo "Falta el SDK privado existente en $SDK. Defini OGT_DB_KMP_SDK_PATH con su ruta real." >&2; exit 1; }
case "$(git -C "$SDK" remote get-url origin)" in
  https://github.com/romancanoniero/db-kmp-sdk|https://github.com/romancanoniero/db-kmp-sdk.git|git@github.com:romancanoniero/db-kmp-sdk.git) ;;
  *) echo 'La carpeta SDK no corresponde a romancanoniero/db-kmp-sdk.' >&2; exit 1;;
esac
test -z "$(git -C "$SDK" status --porcelain)" || { echo 'El SDK tiene trabajo local sin guardar. No se sobrescribe ni se publica una copia incompleta.' >&2; exit 1; }
export OGT_VPS_HOST="${OGT_VPS_HOST:-iankmp-vps}"
[[ "$OGT_VPS_HOST" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ ]] || exit 2
SSH=(ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15)
# The inferred reviewer address must belong to the Mac, not a jump host.
ssh -G "$OGT_VPS_HOST" | awk '
  ($1 == "proxyjump" || $1 == "proxycommand") && $2 != "none" {bad=1}
  END {exit bad ? 1 : 0}
' || { echo 'El acceso usa un proxy SSH. Se requiere configurar la red de revision explicitamente.' >&2; exit 1; }
echo '[1/4] Comprobando codigo y pruebas locales; no se modifica Contabo.'
bash scripts/deploy-ogt-vps.sh --check
echo '[2/4] Preparando acceso privado en la MISMA instalacion OGT.'
"${SSH[@]}" "$OGT_VPS_HOST" 'python3 - --apply' < scripts/prepare-ogt-review.py
echo '[3/4] Compilando y publicando app, backend y CRM con respaldo previo.'
bash scripts/deploy-ogt-vps.sh --deploy
echo '[4/4] Verificando las paginas por HTTPS desde esta Mac.'
SHA="$(git rev-parse HEAD)"
VERIFY="$(mktemp -d)"
trap 'rm -rf "$VERIFY"' EXIT
for page in control empresas; do
  curl --proto '=https' --fail --silent --show-error --max-time 30 \
    "https://onlygoodthings.lat/crm/$page/?release=$SHA" -o "$VERIFY/$page.html"
  grep -q 'id="crm-root"' "$VERIFY/$page.html" || { echo "La URL no devolvio el CRM: $page" >&2; exit 1; }
done
curl --proto '=https' --fail --silent --show-error --max-time 30 \
  "https://onlygoodthings.lat/release.json?release=$SHA" -o "$VERIFY/release.json"
python3 - "$VERIFY/release.json" "$SHA" <<'PY'
import json, sys
with open(sys.argv[1]) as stream:
    release = json.load(stream)
assert release.get('repository') == 'romancanoniero/OGT'
assert release.get('sourceSha') == sys.argv[2]
assert release.get('scope') == 'app-backend-crms'
PY
printf '\nVERSION PUBLICADA Y PAGINAS HTTPS VERIFICADAS: %s\n' "$SHA"
printf '%s\n' 'https://onlygoodthings.lat/crm/control/' 'https://onlygoodthings.lat/crm/empresas/'
printf '%s\n' 'Pendiente de aceptacion: ingreso real con MFA, permisos y funciones de negocio. No se crearon usuarios ni privilegios.'
