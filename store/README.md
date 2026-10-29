# Fichas de tienda — Only Good Things

## Icono (B01 Sostener)
- Master 1024: `store/ios/icon-1024.png` (App Store / iOS AppIcon)
- Play 512: `store/android/icon-512.png`
- Fuente: `design/stitch/logo-opciones/ogt-b01-sostener.png`

## Textos
- iOS: `store/ios/es-MX/`
- Android: `store/android/es-419/`
- Capturas: `store/screenshots/ios/` y `store/screenshots/android/`

## Candidato actual (29/10/2026)

La fuente mantiene `com.onlygoodthings.app`: Android 1.0/versionCode 6 e iOS
1.0/build 5. TestFlight ya contiene 1.0/build 4, cargado el 22/10; ese número no
puede reutilizarse para el candidato actual. Google Play ya contiene los códigos
3, 4 y 5 de la versión 1.0, cargados el 22/10; el código nuevo es 6.
La compilación iOS de dispositivo y
el AAB Android firmado se validan localmente. No equivalen a un nuevo build
procesado en TestFlight o Play.
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

El 29/10 se cargó iOS 1.0/build 5 en App Store Connect, terminó de procesarse y
quedó asignado al grupo interno. Android 1.0/versionCode 6 fue aceptado por Play
y guardado como borrador interno; todavía no reemplaza la versión distribuida.
La cuenta de revisión ordinaria y sus credenciales se conservan fuera de Git;
las notas de App Review ya reemplazan el acceso de laboratorio anterior.

La validación de URLs de Google detectó HTTP 403 en `/privacy` por el filtro de
red prelaunch. El router de documentos permite GET/HEAD de las rutas legales
exactas y los tres archivos de asociación de enlaces. La aplicación, los CRM,
API, archivos de usuarios y escrituras conservan sus restricciones. La excepción
preexistente del webhook de Mercado Pago conserva sus controles de firma.
Para comprobar el cambio y sus límites con la misma versión de Traefik del VPS:

```sh
python3 qa/check-public-documents.py --baseline-ref baba457
```

App Privacy tiene 16 categorías configuradas en borrador, documentadas con sus
fuentes en `store/privacy-data-inventory.json`; no está publicada. La declaración
de ID publicitario de Play ya indica No. Antes del envío siguen pendientes la
revisión/publicación de privacidad, revalidar Data Safety, las declaraciones de
seguridad infantil y ubicación de Play, renovar capturas, comprobar proveedores
reales y habilitar el acceso de revisión. No confundir los documentos públicos
con una apertura de la aplicación.

## Respaldo externo provisional

`scripts/ogt_offsite_backup.py` copia la base y los archivos mediante el alias SSH
existente, excluye secretos y cifra con AES-256-GCM usando la clave privada local.
Usa el volcado transaccional consistente de PostgreSQL y comprueba su SHA-256
contra el archivo del servidor. Verifica que los archivos y el esquema no
cambien durante la captura, y vuelve a descifrar/verificar lo guardado. Las
escrituras normales de cuentas no impiden la copia. No restaura la base real.
El ensayo de recuperación usa
`qa/scripts/verify_offsite_backup.py` con una base local temporal, sin red ni
montajes del host. Docker se necesita para ese ensayo, no para la copia diaria.

El destino elegido es DEV_MAC y la conservación automática es de 30 días. El
instalador local `scripts/install_ogt_offsite_backup.py` prepara únicamente el
LaunchAgent `com.onlygoodthings.offsite-backup`: comprueba cada hora si hace falta
la copia diaria y también al iniciar sesión o montar una unidad. Deben estar
disponibles la Mac, la unidad y el acceso SSH. El estado privado queda en
`~/.local/state/ogt/offsite-backup/status.json`; no se envían alertas a terceros.
Una unidad ausente nunca se sustituye por una carpeta en el disco interno.

La expiración exige una copia reciente verificada. Solo elimina archivos de
copias automáticas completas identificadas por estas herramientas; conserva
copias manuales, secretos, archivos desconocidos y enlaces. El cron del servidor
aplica también 30 días a sus instantáneas de base generadas por OGT, sin afectar
los trabajos de otros proyectos. DEV_MAC es una solución provisional: falta un
destino externo independiente y el procedimiento de recuperación debe contemplar
las bajas de cuentas posteriores a la instantánea antes de reabrir el servicio.

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
