#!/usr/bin/env bash
# Run from a fresh OGT checkout. Reuse the Mac SSH identity, not GitHub secrets.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[[ "${1:-}" == --deploy ]] || { echo 'Uso: bash scripts/publish-ogt-from-mac.sh --deploy [--sdk-committed]' >&2; exit 2; }
[[ "$#" -le 2 && ( -z "${2:-}" || "${2:-}" == --sdk-committed ) ]] || { echo 'Opcion desconocida; no se publica.' >&2; exit 2; }
[[ "$(uname -s)" == Darwin ]] || { echo 'Este comando requiere la Mac con el acceso SSH de OGT.' >&2; exit 1; }
SDK_COPY_ROOT=""
VERIFY=""
cleanup() {
  if [ -n "$VERIFY" ]; then rm -rf -- "$VERIFY"; fi
  if [ -n "$SDK_COPY_ROOT" ]; then rm -rf -- "$SDK_COPY_ROOT"; fi
}
trap cleanup EXIT
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
if [[ "${2:-}" == --sdk-committed ]]; then
  echo 'Usando SOLO el ultimo commit local del SDK; los cambios pendientes quedan fuera de esta publicacion.'
  echo 'El SDK original no se guarda, limpia ni modifica. Las pruebas de compilacion siguen siendo obligatorias.'
  SDK_COPY_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/ogt-sdk-committed.XXXXXX")"
  SDK_REVISION="$(python3 scripts/sdk_committed_snapshot.py "$SDK" "$SDK_COPY_ROOT/sdk")"
  SDK="$SDK_COPY_ROOT/sdk"
  export OGT_DB_KMP_SDK_PATH="$SDK"
  printf 'Commit SDK en la copia independiente: %s\n' "$SDK_REVISION"
fi
test -z "$(git -C "$SDK" status --porcelain)" || {
  echo 'El SDK tiene cambios locales. Para usar SOLO el ultimo commit sin tocar esos cambios, agrega --sdk-committed.' >&2
  exit 1
}
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
echo '[2/4] Verificando el dominio desde la Mac y preparando acceso privado en la MISMA instalacion OGT.'
EXPECTED_SERVER="$(python3 - <<'PY'
import ipaddress, socket, sys
rows = socket.getaddrinfo('onlygoodthings.lat', 443, type=socket.SOCK_STREAM)
addresses = sorted({str(ipaddress.ip_address(row[4][0])) for row in rows})
if len(addresses) != 1:
    print('El dominio debe resolver a una unica direccion publica para este procedimiento.', file=sys.stderr)
    raise SystemExit(1)
address = ipaddress.ip_address(addresses[0])
if not address.is_global:
    print('La direccion publicada del dominio no es global.', file=sys.stderr)
    raise SystemExit(1)
print(address)
PY
)"
ACTUAL_SERVER="$("${SSH[@]}" "$OGT_VPS_HOST" 'set -- $SSH_CONNECTION; if [ "$#" -eq 4 ]; then printf "%s\\n" "$3"; fi')"
test -n "$ACTUAL_SERVER" || { echo 'No se pudo obtener la direccion de destino de la sesion SSH.' >&2; exit 1; }
test "$ACTUAL_SERVER" = "$EXPECTED_SERVER" || {
  echo 'El dominio resuelto por la Mac no apunta al mismo destino que la sesion SSH. No se publica.' >&2
  exit 1
}
"${SSH[@]}" "$OGT_VPS_HOST" "python3 - --apply --expected-server '$EXPECTED_SERVER'" < scripts/prepare-ogt-review.py
echo '[3/4] Compilando y publicando app, backend y CRM con respaldo previo.'
bash scripts/deploy-ogt-vps.sh --deploy
echo '[4/4] Verificando las paginas por HTTPS desde esta Mac.'
SHA="$(git rev-parse HEAD)"
VERIFY="$(mktemp -d)"
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
