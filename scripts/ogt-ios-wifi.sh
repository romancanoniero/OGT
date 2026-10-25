#!/usr/bin/env bash
# OGT: update, build and install the existing app on a paired iPhone.
# Never resets a repository, deletes device data or uses TestFlight.
set -euo pipefail

DEVICE="${OGT_IOS_DEVICE:-}"
DO_UPDATE=1
case "$@" in
  *--help*) echo "Uso: bash scripts/ogt-ios-wifi.sh [--device 'Mi iPhone'] [--no-update] [--list-devices]"; exit 0 ;;
esac
while [ "$#" -gt 0 ]; do
  case "$1" in
    --device) test "$#" -gt 1 || { echo "Falta el dispositivo" >&2; exit 2; }
              DEVICE="$2"; shift 2 ;;
    --no-update) DO_UPDATE=0; shift ;;
    --list-devices) xcrun devicectl list devices; exit $? ;;
    *) echo "Parámetro desconocido: $1" >&2; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$ROOT"
test -d .git || { echo "No encontré el repositorio Git" >&2; exit 1; }
case "$(git remote get-url origin)" in
  https://github.com/romancanoniero/OGT|https://github.com/romancanoniero/OGT.git|git@github.com:romancanoniero/OGT.git) ;;
  *) echo "ABORTADO: el repositorio no es romancanoniero/OGT" >&2; exit 1 ;;
esac

if [ "$DO_UPDATE" -eq 1 ]; then
  test "$(git branch --show-current)" = main || {
    echo "ABORTADO: pasá a main manualmente; no cambio ramas." >&2; exit 1;
  }
  test -z "$(git status --porcelain --untracked-files=no)" || {
    echo "ABORTADO: tenés cambios locales. Guardalos antes de actualizar." >&2
    git status --short
    exit 1
  }
  echo "Actualizando origin/main sin borrar ni sobrescribir cambios locales..."
  git fetch origin main
  git merge --ff-only origin/main
  # The updated script is reloaded after the fast-forward.
  if [ -n "$DEVICE" ]; then
    exec /bin/bash "$ROOT/scripts/ogt-ios-wifi.sh" --no-update --device "$DEVICE"
  else
    exec /bin/bash "$ROOT/scripts/ogt-ios-wifi.sh" --no-update
  fi
fi

for cmd in xcodebuild xcrun python3 plutil codesign; do
  command -v "$cmd" >/dev/null || { echo "Falta $cmd en la Mac" >&2; exit 1; }
done
case "$(xcode-select -p)" in
  */Xcode*.app/Contents/Developer) ;;
  *) echo "Seleccioná el Xcode completo con xcode-select." >&2; exit 1 ;;
esac
MAJOR="$(xcodebuild -version | sed -n 's/^Xcode \([0-9][0-9]*\).*/\1/p')"
test -n "$MAJOR" && test "$MAJOR" -ge 26 || {
  echo "Para la versión actual de iOS se requiere Xcode 26+." >&2; exit 1;
}

SDK="${OGT_DB_KMP_SDK_PATH:-$HOME/AndroidStudioProjects/db-kmp-sdk}"
test -f "$SDK/settings.gradle.kts" || {
  echo "No encuentro el SDK privado: $SDK" >&2
  echo "Indicá OGT_DB_KMP_SDK_PATH=/ruta/db-kmp-sdk" >&2; exit 1;
}
test -z "$(git -C "$SDK" status --porcelain --untracked-files=no)" || {
  echo "Hay modificaciones en el SDK privado; no las voy a cambiar." >&2; exit 1;
}
case "$SDK" in *" "*)
  echo "La ruta del SDK contiene espacios; seleccioná una sin espacios." >&2; exit 1 ;;
esac
# The Xcode project invokes Gradle in its build phase. Pass the SDK dependency.
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.project.ogt.dbKmpSdkPath=$SDK"

CONFIG="$ROOT/iosApp/iosApp/GoogleService-Info.plist"
if [ ! -f "$CONFIG" ]; then
  for candidate in "${OGT_IOS_FIREBASE_PLIST:-}" \
     "/Volumes/DEV_MAC/secrets/OGT/GoogleService-Info.plist" \
     "$ROOT/secrets/GoogleService-Info.plist" "$HOME/GoogleService-Info.plist"; do
    if [ -n "$candidate" ] && [ -f "$candidate" ]; then
      cp "$candidate" "$CONFIG"; chmod 600 "$CONFIG"; break
    fi
  done
fi
if [ ! -f "$CONFIG" ]; then
  echo "Falta GoogleService-Info.plist para la app iOS com.onlygoodthings.app." >&2
  echo "Descargalo desde Firebase Console > Configuración del proyecto > Tus apps > iOS." >&2
  echo "Guardalo en: $CONFIG (está excluido de Git)." >&2
  exit 1
fi
python3 - "$CONFIG" <<'PY'
import pathlib, sys
from scripts.ogt_ios_firebase_config import validated_plist
validated_plist(pathlib.Path(sys.argv[1]).read_bytes())
print("Firebase iOS: proyecto y bundle ID verificados.")
PY
plutil -lint "$CONFIG"

TEMP="$(mktemp -d "${TMPDIR:-/tmp}/ogt-device.XXXXXX")"
trap 'rm -rf "$TEMP"' EXIT
echo "Buscando iPhone emparejado (USB o Wi-Fi)..."
xcrun devicectl list devices --json-output "$TEMP/devices.json" >/dev/null
python3 - "$TEMP/devices.json" "$DEVICE" > "$TEMP/selection" <<'PY'
import json,sys
devices=json.load(open(sys.argv[1])).get("result",{}).get("devices",[])
rows=[]
for device in devices:
    hw=device.get("hardwareProperties") or {}
    if str(hw.get("platform","")).lower() not in ("ios","iphoneos"):
        continue
    core=device.get("identifier") or ""
    udid=hw.get("udid") or core
    name=(device.get("deviceProperties") or {}).get("name") or ""
    if core and udid: rows.append((str(core),str(udid),str(name)))
wanted=sys.argv[2].strip().casefold()
chosen=[r for r in rows if wanted in [v.casefold() for v in r]] if wanted else rows
if len(chosen)!=1:
    print("Elegí un único iPhone con --device 'Nombre del iPhone':",file=sys.stderr)
    for _,udid,name in rows: print(" - "+name+" ("+udid+")",file=sys.stderr)
    print("Emparejalo antes en Xcode > Window > Devices and Simulators.",file=sys.stderr)
    sys.exit(2)
print(*chosen[0],sep="\n")
PY

CORE_ID="$(sed -n '1p' "$TEMP/selection")"
UDID="$(sed -n '2p' "$TEMP/selection")"
NAME="$(sed -n '3p' "$TEMP/selection")"
DERIVED="$ROOT/build/ogt-ios-device"
mkdir -p "$DERIVED"
echo "Compilando y firmando Only Good Things para $NAME..."
echo "OGT: $(git rev-parse --short HEAD); SDK: $(git -C "$SDK" rev-parse --short HEAD)"
xcodebuild \
  -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -sdk iphoneos -destination "platform=iOS,id=$UDID" \
  -derivedDataPath "$DERIVED" \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM=WER3D825VB CODE_SIGN_STYLE=Automatic CODE_SIGNING_ALLOWED=YES \
  build 2>&1 | tee "$DERIVED/build.log"

APP="$DERIVED/Build/Products/Debug-iphoneos/iosApp.app"
test -d "$APP" || { echo "Xcode no generó $APP" >&2; exit 1; }
codesign --verify --deep --strict "$APP"
echo "Instalando la app firmada por Wi-Fi/USB sin borrar datos..."
xcrun devicectl device install app --device "$CORE_ID" "$APP"
if ! xcrun devicectl device process launch --device "$CORE_ID" com.onlygoodthings.app; then
  echo "Instalación correcta. Desbloqueá el iPhone y abrí OGT manualmente." >&2
fi
echo "OGT actualizado e instalado en $NAME."
