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

La fuente mantiene `com.onlygoodthings.app`: Android 1.0/versionCode 10 e iOS
1.0/build 9. Estos candidatos locales conservan la fuente debajo de la imagen y
corrigen el zoom del mapa: amplían las teselas disponibles hasta el zoom 19 sin
pedir niveles sin cartografía, y cancelan las descargas al cambiar de cámara.
Las consolas recibieron TestFlight build 8 y Play código 9; este último quedó
guardado como borrador interno. Los nuevos candidatos 9/10 se preparan localmente
con identificación de ilustraciones generadas con IA y la política sin documentos.
No se declara una carga a las tiendas hasta comprobar su recepción.
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

App Privacy tiene 16 categorías publicadas, documentadas con sus fuentes en
`store/privacy-data-inventory.json`. Data Safety tiene 17 tipos guardados y la
declaración de seguridad infantil fue guardada con confirmación del operador.
La declaración de ID publicitario de Play indica No. Antes del envío siguen
pendientes la declaración y demostración de ubicación de Play, renovar capturas,
comprobar los proveedores reales y habilitar el acceso de revisión. No confundir
los documentos públicos con una apertura de la aplicación.

## Derechos de contenido: declaración pendiente

`attributionUrl` es el enlace opcional de créditos de publicaciones e historias;
se conserva al guardar, leer, editar, cachear y moderar. Es independiente de
`sourceUrl`, que mantiene la clasificación de noticias y los enlaces de homenajes.
La migración 36 agrega una columna nullable compatible con clientes anteriores.
Omitir el campo al editar conserva el crédito; enviarlo vacío lo borra.

El acuerdo y el formulario aclaran la declaración de derechos del usuario,
las obligaciones propias de OGT y la denuncia y retirada. Dar crédito no crea
una autorización. El inventario `editorial-media-rights.json` relaciona las 19
fotografías precargadas con las fichas consultadas en Wikimedia Commons: cinco
licencias CC y catorce fichas que indican dominio público. Se compararon los
originales del servidor: 17 coinciden byte a byte y dos tienen los mismos píxeles
con metadatos distintos. El anexo de créditos identifica autores, licencias,
procedencias y adaptaciones; no certifica derechos que no estén acreditados.

No aceptar todavía la declaración afirmativa de Apple. Sigue pendiente evaluar
la aplicación o exención del dominio público pagante en Argentina, el alcance
territorial de Favaloro, Mercedes Sosa y Carlos Mugica, y los derechos de imagen
que no resuelve una licencia de copyright. La distribución inicial en Argentina
no demuestra por sí sola que la web sea inaccesible desde otros países.

La aceptación Android se realiza únicamente en el emulador autorizado
`OgtReleaseAcceptance20261029`, con la cuenta ordinaria de revisión y una
ubicación ficticia. El ingreso y la concesión de «Siempre» funcionaron; el
botón para omitir verificación se movió fuera del contenido desplazable para
evitar que quedara detrás de la navegación del sistema. La pantalla preexistente
de identidad fue reemplazada en la candidata iOS 9 / Android 10 por una explicación
veraz de las comprobaciones disponibles. Por decisión del titular, OGT no solicita
DNI ni comprobantes y no promete una verificación civil o de antecedentes.

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
destino externo independiente para una etapa posterior. El registro cifrado de
bajas se actualiza cada hora y conserva las huellas conocidas aunque se restaure
el origen. El procedimiento de `docs/OPERATIONS.md` exige reconciliar una candidata
sin tráfico con evidencia que cubra la última escritura posible del origen antes
de considerar su reapertura. No restaura ni abre automáticamente la base real.

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

## Ajuste de identidad y recursos editoriales

El titular confirmó conservar OGT sin solicitar documentos personales. La pantalla
de identidad explica el alcance del ingreso y de la ubicación; no recibe archivos,
no valida un domicilio fijo ni promete una insignia inexistente. Se conserva el
ingreso al feed y se puede consultar la explicación desde Ajustes. La prueba real
de ingreso y baja con Apple queda pendiente por decisión del titular.

El inventario editorial contiene 13 ilustraciones generadas para OGT y seis
fotografías con licencia Creative Commons. Se conservan los créditos y las
condiciones de las fotografías. Las ilustraciones se identifican junto al medio,
y sus instrucciones, originales y huellas están en `editorial-media/provenance.json`.
Esto no es una garantía de ausencia universal de reclamos ni acredita otros activos.
