# Fichas de tienda — Only Good Things

## Icono (B01 Sostener)
- Master 1024: `store/ios/icon-1024.png` (App Store / iOS AppIcon)
- Play 512: `store/android/icon-512.png`
- Fuente: `design/stitch/logo-opciones/ogt-b01-sostener.png`

## Textos
- iOS: `store/ios/es-MX/`
- Android: `store/android/es-419/`
- Capturas: `store/screenshots/ios/` y `store/screenshots/android/`

## Candidato actual (27/10/2026)

La fuente mantiene `com.onlygoodthings.app`: Android 1.0/versionCode 5 e iOS
1.0/build 4. La compilación iOS de dispositivo y el AAB Android firmado se validaron
localmente. No equivalen a un nuevo build procesado en TestFlight o Play.
Antes de subir, consultar los números existentes en ambas consolas y elegir un
número nuevo si alguno ya fue utilizado. Conservar la firma actual y la de Play.

La aplicación usa Firebase y backend real; el acceso antes de lanzamiento está
restringido por red. No enviar a revisión las credenciales de laboratorio que se
conservan abajo como referencia histórica. Preparar una cuenta real de revisión,
con permisos ordinarios, y comprobar su acceso desde la red del revisor. El
segundo factor se reserva a los CRM; la cuenta de revisión móvil no debe recibir
permisos administrativos.

Antes de solicitar revisión: validar Apple/Google/SMS, baja y exportación,
notificaciones/enlaces y permisos en dispositivos; renovar las capturas con
contenidos autorizados del candidato; completar App Privacy y Data Safety según
la recopilación efectiva. El manifiesto iOS incluido declara motivos de APIs,
no reemplaza esos formularios. No publicar notas ni capturas que describan una
versión demo como si fuera la actual.

## Historial de distribución anterior
- Versión de prueba: **0.2.0** (iOS build 2 / Android versionCode 2).
- iOS: TestFlight, bundle `com.onlygoodthings.app`, team `WER3D825VB`, Apple ID `6813852319`. Build `3104bf23-603e-45e9-9359-618d11740b18`.
- Android: pista interna de Play, package `com.onlygoodthings.app`, app `4974603618070417614`. Enlace: https://play.google.com/apps/internaltest/4701435613898180947
- Keystore de Play (no está en Git): `/Volumes/DEV_MAC/secrets/OGT/`
- Capturas 6,5": `store/screenshots/ios-65/` (1284×2778).

## Referencia histórica de laboratorio (solo QA local)
- correo `vecino.emulador@goodthings.test`
- clave `GoodThings123`
- o perfil preview Mariana (capturas `-OgtStorePreview` / `ogt.store_preview=1`)
- pantalla: `-OgtStoreScreen parking|animals|skills|publish|map|petsmap` / extra `ogt.store_screen`

## Capturas iOS (iPhone 17 Pro, 1206×2622)
- `store/screenshots/ios/01-feed.png`
- `store/screenshots/ios/02-mascotas.png`
- `store/screenshots/ios/03-parking.png` (GPS del sim a veces no llega; Android 03 es la referencia)
- `store/screenshots/ios/04-skills.png`
- `store/screenshots/ios/05-dock.png`
- `store/screenshots/ios/06-publish.png`

## Capturas Android (Pixel 6a, 1080×2400)
- `store/screenshots/android/01-feed.png`
- `store/screenshots/android/02-mascotas.png`
- `store/screenshots/android/03-parking.png`
- `store/screenshots/android/04-skills.png`
- `store/screenshots/android/05-dock.png`
- `store/screenshots/android/06-publish.png`
