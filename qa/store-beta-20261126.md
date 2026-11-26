# Distribución de prueba de OGT — 26/11/2026

Solicitud: subir la versión de prueba a TestFlight y Google Play. Se conserva `com.onlygoodthings.app`, versión comercial 1.0, código de `main` en `ca1bb98` (corrección funcional GPS `4c00dc6`). No se modifica código funcional, permisos, identidades, certificados, versiones mínimas, contratos ni datos para distribuirla. No se publica en producción ni se despliega el servidor.

## Android

- App Bundle firmado 1.0 (17), generado con `:composeApp:bundleRelease` y comprobado con `jarsigner`; integridad ZIP válida. Se conservan ARM64, ARMv7, x86 y x86_64, mínimo API 26 y objetivo API 36.
- Destino: **Prueba interna** de Only Good Things. Estado observado: **Activo / Disponible para testers internos**, versión «1.0 (17) · Invitaciones, feed y GPS».
- Se actualiza el borrador interno existente. El paquete 12 se retira únicamente de ese lanzamiento porque Google lo rechaza al quedar completamente sustituido por 17; permanece en la biblioteca de artefactos. La versión 2 se conserva en el historial. No se modifica el borrador de producción 5.
- Se conservan las notas anteriores en español e inglés y se agregan invitaciones, convocatorias, categorías, continuidad del feed, rendimiento y activación de ubicación.
- La lista de acceso existente «Lista Testing», con 11 usuarios, permanece seleccionada. No se agregan usuarios ni se modifica otra lista. Enlace de acceso existente: https://play.google.com/apps/internaltest/4701435613898180947
- Google verifica cero dispositivos que pierden compatibilidad, conservando 11.524 teléfonos, 6.567 tablets, 6 TV, 30 automóviles, 77 Chromebook y 2 Android XR. No hay errores de validación. Los dos avisos no bloqueantes son ausencia de mapping R8 (la compilación mantiene `isMinifyEnabled=false`) y de símbolos nativos de las bibliotecas precompiladas.

## iOS

- Archivo Release ARM64 `build/ios-gps-activation/OGT.xcarchive`; IPA App Store 1.0 (15) en `build/store-beta-20261126-ca1bb98/ios/iosApp.ipa`. Perfil administrado por Xcode vigente hasta 17/08/2027, sin dispositivos limitados, `get-task-allow=false` y push de producción. Se reutilizan la identidad y los dominios asociados existentes.
- Verificados firma profunda/estricta, arquitectura/plataforma, recursos Compose (143), Assets.car, Firebase correcto, manifiesto de privacidad conservado, integridad ZIP y UUID coincidente del ejecutable/dSYM.
- Entrega de Apple **aceptada sin errores**. Identificador de entrega: `294d5815-5d83-4de2-9f1e-9ec9356e9000`. El único aviso de carga es el requisito futuro de SDK iOS 27 a partir de abril de 2027; la entrega actual con SDK 26.2 fue aceptada.
- Procesamiento final **VALID**; estado interno **IN_BETA_TESTING**, versión comercial 1.0, compilación 15, sin caducar y con declaración de cifrado conservada. La API confirma que el grupo existente «Internos» contiene el build; la consola muestra la compilación 15 asociada a Internos y una invitación existente. Queda disponible para ese grupo con su acceso automático habitual. No se agregan testers ni se cambia su configuración de notificación. Se conserva el grupo «Externos» y su enlace público deshabilitado; no se envía a revisión externa ni se publica en App Store.

## Evidencia y límites

La corrección distribuida conserva el resultado de 145 pruebas Android y 168 shared sin fallos; además se ejecutan compilación y lint vital Release de Android. No se repiten pruebas sin cambios funcionales. Las verificaciones de las tiendas y de firma comprueban distribución, compatibilidad declarada y empaquetado; no equivalen a recorrer cada función en dispositivos físicos.

Hashes y estados detallados se registran localmente en `qa/artifacts/store-beta-20261126.json`, excluido de Git. Los paquetes están en `build/`, también excluido. No se guardan claves, tokens, certificados privados ni contactos de testers en la evidencia versionada.
