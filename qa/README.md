# OGT: verificación del candidato integrado

Actualización: 27/10/2026 (Argentina). Rama `codex/beta-candidate-20261027`;
SDK `0a881b7a15c9a7d99b77da8547aabe2dd493a8b0`. Los apartados inferiores conservan
el registro histórico: no reemplazan la evidencia de la revisión actual.

## Evidencia actual

- Regresión completa: **50/50** Chromium y WebKit móvil, API/PostGIS/Redis reales
  locales y Firebase emulado. Informe: `build/launch-candidate-e2e.json`.
- Backend: 60 pruebas; dominio compartido: 142; negocio con base aislada: 8.
- Cliente Android: 47 pruebas JVM portables y las **56 pruebas comunes dentro
  de Android**, incluyendo las de imagen, sesión y archivos que requieren SO real.
- Node: 99; Python: 114. Sin fallos en estas ejecuciones.
- API de historias: 13 grupos; bloqueo personal: 7; empresas: 5.
- iOS: compilación firmada para dispositivo, `build/launch-final-ios-device.log`.
- Android: AAB release firmado en `composeApp/build/outputs/bundle/release/`;
  firma verificada. Estos resultados no significan subida a las tiendas ni deploy.
- Mercado Pago: cuenta de prueba verificada y checkout sandbox creado sin pago.
  Reintentos, duplicados, devoluciones y deuda se prueban contra un proveedor
  controlado en las pruebas SQL. El pago real del sandbox y la entrega de
  una notificación firmada por Mercado Pago todavía requieren aceptación.
- Apple: proveedor configurado; compilaciones y pruebas de contrato aprobadas.
  Ingreso, vinculación, reingreso y baja con una cuenta Apple real pendientes.

## Cómo repetir las comprobaciones actuales

Conservar las credenciales fuera de Git y usar exclusivamente el entorno aislado
descrito más abajo. No usar datos de producción para satisfacer fixtures.
El SDK local debe estar en la revisión indicada al principio. No regenerar la
instalación de backend mientras la API de QA usa sus archivos: detener solo ese
proceso antes de `:backend:installDist` y reiniciarlo después.

```sh
node --test qa/*.test.mjs
python3 -m unittest discover -s qa -p 'test_*.py'
./gradlew :backend:test :shared:jvmTest :composeApp:testDebugUnitTest
```

Con API en 19083, web en 19082 y emulador Auth en 19099, generar identidades
sintéticas para los casos de historias, bloqueo y negocio:

```sh
node qa/scripts/stories-e2e.mjs
node qa/scripts/user-blocks-e2e.mjs
node qa/scripts/business-e2e.mjs
cd qa
OGT_FULL_E2E=1 npm test
```

La restauración de un respaldo de Contabo se ensayó en una base temporal separada:
coincidieron usuarios, publicaciones, campañas y cantidad de tablas; PostGIS y
salud del backend se comprobaron. La base temporal se retiró sin modificar la
base original. El recibo privado está en `build/launch-restore-rehearsal.log`.

`business-e2e.mjs` requiere la configuración sandbox de Mercado Pago en la API;
crea un checkout sin pagar. Los archivos `build/*-qa-account.json` son privados,
contienen tokens exclusivamente de QA, no deben imprimirse ni entrar en Git.
Las afirmaciones MFA de esas identidades son fixtures del emulador: no certifican
el enrolamiento de segundo factor del proveedor real.

Para las ocho pruebas transaccionales, crear una base separada en el PostGIS de
QA, con nombre `ogt_business_qa_<sufijo>`, aplicar las migraciones de esquema
excluyendo la importación editorial 14 y ejecutar:

```sh
OGT_BUSINESS_TEST_JDBC=jdbc:postgresql://127.0.0.1:19432/ogt_business_qa_local \
./gradlew :backend:businessIntegrationTest
```

Para Android, las pruebas de almacenamiento se instalan exclusivamente bajo
`com.onlygoodthings.app.qa`, con Firebase sintético y servicios externos apagados.
No se ejecutan dentro del paquete del usuario:

```sh
ANDROID_SERIAL=<emulador-Android> ./gradlew -Pogt.instrumentedQa=true \
-I qa/android-instrumented.init.gradle :composeApp:connectedDebugAndroidTest
```

La configuración utiliza el árbol de pruebas de Kotlin; conserva la jerarquía
normal de `iosMain`/`iosTest`. No sustituirla por una dependencia manual que
suprima esa jerarquía. La configuración Firebase y la clave release reales se
usan únicamente al generar la distribución, nunca para el paquete de QA.

## Límites y diferencias intencionales

La empresa debe operar con MFA y permisos vigentes por organización. Las cargas,
reservas, cambios de campaña y cupones llevan identificador de operación; las
versiones antiguas que intenten mover saldo sin él reciben una solicitud de
actualización. El historial permite retomar pagos pendientes; nunca acredita
saldo basándose en el regreso del navegador. La consulta del historial funciona
incluso si el proveedor está temporalmente deshabilitado. Los códigos de cupón
quedan restringidos a los operadores autorizados, conservando los indicadores
para lectores. Las actualizaciones de saldo conservan los campos escritos.

La verificación Android de SMS permanece habilitada en release; la excepción de
emulador se limita a Debug. Release usa HTTPS; Debug conserva el acceso HTTP de
laboratorio. Se agregó el manifiesto iOS de APIs con motivos requeridos y se
registraron en Firebase las huellas públicas de la firma local release, manteniendo
las de Play. El manifiesto no sustituye los formularios de privacidad de las tiendas.

El alta por solicitud y revisión independiente de Control está implementada y
probada por HTTP y en ambos navegadores: la solicitud no concede acceso, la
aprobación crea una empresa verificada y un titular, y rechazar o retirar no
concede permisos. Reintentos, fiscal duplicado, aprobación propia, exportación y
baja se verifican en la base aislada. La migración aditiva es la 34.

Siguen pendientes la gestión completa de roles y transferencia de titularidad,
aceptación de proveedores y dispositivos reales, distribución de una revisión
única, despliegue privado, restauración ensayada y aceptación operativa. Conservar
el acceso privado de prelaunch; estas pruebas no autorizan abrir la aplicación.

## Registro histórico y procedimientos anteriores

# OGT: verificación de lanzamiento

Base de producción recuperada: `recovery/vps-baseline-20261019` (`3a726d6`).
Trabajo de lanzamiento: `launch/readiness-20261019`. No desplegar esta rama todavía.

Última regresión local (19/10/2026): **22/22 pruebas** en Chromium y WebKit con
emulación móvil, con Firebase Auth emulado, PostGIS y Redis. Incluye reserva
simultánea de estacionamiento (un solo ganador), permisos de terceros, liberación,
cierre por proximidad y confirmación repetida sin duplicar puntos. Las 16 pruebas
del backend también pasan. No sustituye pruebas de proveedores reales ni dispositivos.

Ampliación posterior: 2/2 pruebas nuevas de recuperación de contraseña pasan en
ambos navegadores. Se solicita el enlace desde la interfaz, se obtiene únicamente
del emulador local y se consume mediante su API (no se prueba la página de correo
alojada por Firebase). Se verifica enlace de un solo uso, rechazo de la contraseña
anterior, ingreso con la nueva y conservación del perfil. La prueba espera que el
cierre de sesión llegue a la introducción antes de avanzar. No se modificó la app.

## Entorno aislado

Requisitos: JDK 17/21, SDK Android 36, Node 22, Docker con espacio disponible y el SDK
`db-kmp-sdk` indicado por `ogt.dbKmpSdkPath` (revisión utilizada: `6a021676d37b9e76137090e14dfaea975dee068f`).
Las credenciales de producción y sus datos no se utilizan. El proyecto Firebase es
`demo-ogt-qa`. Los puertos de QA se publican exclusivamente en loopback.

Desde la raíz:

```sh
docker compose -f qa/compose.yml up -d --wait
./gradlew :backend:test :shared:jvmTest :backend:installDist
```

En terminales independientes:

```sh
cd qa
npm ci
npm run auth-emulator
```

```sh
OGT_ENV=test OGT_ALLOW_DEV_TOKENS=false \
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19099 \
OGT_FIREBASE_PROJECT_ID=demo-ogt-qa \
OGT_JDBC_URL=jdbc:postgresql://127.0.0.1:19432/onlygoodthings \
OGT_DB_USER=ogt OGT_DB_PASSWORD=ogt_qa_local_only \
OGT_REDIS_URL=redis://127.0.0.1:19379 \
OGT_MEDIA_DIR=qa/artifacts/media OGT_PUBLIC_BASE=http://localhost:19082 \
PORT=19083 backend/build/install/backend/bin/backend
```

```sh
cd qa
npm run web
```

Pruebas:

```sh
node --test qa/auth-unit.test.mjs
sh qa/check-nginx.sh
cd qa
npx playwright install chromium webkit
npm test
OGT_FULL_E2E=1 npm test
```

`npm test` sin `OGT_FULL_E2E=1` ejecuta la interfaz anónima y omite explícitamente
el circuito completo. No equivale a una certificación E2E. Los casos completos
utilizan Firebase emulado, API real y base de datos real; el circuito descrito pasó en ambos navegadores.
Los navegadores bloquean destinos externos, incluidos producción, proveedores OAuth y SMS.
Las pruebas completas generan usuarios sintéticos únicamente en el entorno de QA.

Para liberar el entorno aislado: detener los procesos de las terminales y ejecutar
`docker compose -f qa/compose.yml down --volumes`. Nunca usar el compose de producción para QA.

## Evidencia obtenida el 19 de octubre de 2026

| Comprobación | Resultado |
|---|---|
| Dominio compartido antes de cambios | 118 aprobadas, 4 fallidas |
| Dominio compartido después de correcciones | 122 aprobadas |
| Backend: autenticación, imágenes y subida HTTP | 16 aprobadas |
| Lógica de sesión web | 5 aprobadas |
| Interfaz anónima Chromium/WebKit móvil | 4 aprobadas |
| Registro → API → persistencia → avatar → nueva sesión | Aprobado en Chromium y WebKit móvil |
| API: sesiones falsas y subidas anónimas | Aprobado en ambos proyectos de navegador |
| Publicación con foto, comentarios y permisos entre cuentas | Aprobado en Chromium y WebKit móvil |
| Trueque: coincidencias, repetición, apoyo y cierre | Aprobado; conserva historial y retira acciones activas al cerrar |
| Mensajes privados, roles y subidas concurrentes/inválidas | Aprobado con cuentas sintéticas y API local |
| Accesibilidad automática del acceso | Cero incidencias axe en ambos navegadores; no certifica otras pantallas |
| Nginx: sintaxis y rutas de medios/API | Aprobado en contenedor con upstream simulado |
| Adopciones: publicación con foto desde UI, edición y cierre propio | Aprobado en Chromium y WebKit móvil |
| Mascotas perdidas: datos requeridos, cercanía y cierre | Aprobado por API real tras registro UI, en ambos navegadores |
| Android: compilación completa | Bloqueada por ausencia de `google-services.json` |
| Android: compilación Kotlin omitiendo generación Firebase | Aprobada; no equivale a APK/configuración verificados |
| Producción: consulta de configuración sin mutaciones | Laboratorio habilitado; ruta Firebase Admin ausente |
| Reconstrucción backend de Contabo | 175/175 clases equivalentes sin depuración; 174 idénticas byte a byte |
| Reconstrucción biblioteca compartida de Contabo | 429/429 clases idénticas byte a byte |

Los informes se generan en `backend/build/reports/tests`, `shared/build/reports/tests`
y `qa/playwright-report`. Capturas y trazas están en `qa/test-results`. La evidencia
de la VPS se conserva localmente bajo `.recovery`; no se publica ni se suben binarios.

Medición con imagen sintética texturada y EXIF: 6.135.720 → 1.905.840 bytes (68,9 % de reducción).
Un WebP transparente de 44 bytes se conservó en 44 bytes. No es una estimación de ahorro de producción.

Al recuperar espacio se restableció Docker y se ejecutaron los ocho casos Playwright
con `OGT_FULL_E2E=1`: todos aprobados. Los 16 tests del backend también se repitieron
correctamente. Para alojar navegadores en el disco externo, usar
`PLAYWRIGHT_BROWSERS_PATH=/Volumes/DEV_MAC/OGT/.recovery/playwright` tanto al instalar
como al ejecutar Playwright.

La ampliación posterior completa 16 casos Playwright aprobados, con Firebase emulado,
API y base reales locales. Incluye publicación desde la interfaz y controles de API
entre cuentas distintas; no todos los pasos se realizan mediante botones. Se repitieron
los 16 tests de backend y los 5 de sesión web; los 122 del dominio compartido permanecen
aprobados (Gradle los consideró actualizados). No hubo despliegue.

El volumen DEV_MAC sufrió desconexiones durante la sesión. Tras recuperarlo se reinició
la API, cuyo directorio de trabajo anterior impedía escribir temporales, y la batería
completa volvió a pasar. Ante otro desmontaje, reiniciar los procesos de QA antes de
interpretar los fallos; no reconstruir installDist mientras esa API esté ejecutándose.

La tanda de mascotas amplía la batería a 20 casos Playwright, todos aprobados en la
misma ejecución. Se volvieron a aprobar los 16 tests de backend. Incluye adopción desde
la interfaz, foto persistente, bloqueo de edición ajena, cierre sin autoasignarse puntos,
rechazo de alertas sin señas o coordenadas, distancias y exclusión de alertas cerradas.
No cubre todavía mapas/geocodificación externos ni notificaciones en dispositivos reales.

La inicialización aislada excluye únicamente `14_elpidio_homenaje.sql`, una importación
editorial que referencia una identidad existente de producción. Las migraciones originales
se conservan. El script se copia dentro del contenedor para evitar permisos de ejecución
del volumen macOS. Esto no certifica una instalación limpia de producción.

## Diferencias intencionales de esta rama

- Firebase define el nombre al crear la cuenta; los siguientes inicios de sesión
  conservan el nombre editado en OGT. Antes el nombre antiguo sobrescribía el cambio
  al recargar. El E2E verifica nombre y foto después de guardar, recargar y volver a entrar.
  Email, teléfono y reclamo de reconocimientos mantienen el comportamiento previo.
- Los pedidos cerrados de Trueque conservan su publicación histórica, pero el feed
  deja de ofrecer acciones de pedido activo y coincidencias con habilidades.
- La búsqueda de mascotas cercanas utiliza una respuesta serializable con los mismos
  campos JSON. Antes fallaba al responder una lista de mapas con tipos mezclados.
  Se conserva la compatibilidad con fichas antiguas cuyo tamaño es nulo.
- La pantalla de acceso identifica su contenido principal para lectores de pantalla,
  manteniendo cabecera y pie fuera de ese bloque. La referencia numérica decorativa
  se excluye del árbol accesible; el aspecto visual permanece igual.
- Producción falla al iniciar si permite tokens de laboratorio o emulación Firebase.
  Firebase Admin usa credenciales configuradas o Application Default Credentials;
  los tokens inválidos reciben 401. No se cambió la configuración de la VPS.
- La web no sustituye JWT fallidos por tokens de laboratorio; espera la restauración
  de Firebase y renueva tokens. El laboratorio sigue disponible solo en localhost
  con `window.OGT_ALLOW_DEV_LOGIN = true` y backend de desarrollo explícito.
- Localhost deja de apuntar automáticamente a producción. El cliente nativo usa HTTPS
  y requiere habilitación explícita para los tokens de desarrollo.
- Toda subida declarada como imagen pasa por un optimizador común. JPEG/PNG/WebP
  estáticos se validan y se intentan reducir, con objetivo de 2048 px, orientación EXIF
  y transparencia conservadas. Si la conversión aumenta bytes, se conserva el original.
  Por eso 2048 px es un objetivo, no un límite garantizado para una imagen ya más pequeña en bytes.
- Se rechazan imágenes corruptas, con formato declarado distinto del contenido o
  superiores a 40 megapíxeles. Se mantiene el máximo de 8 MB por archivo y la ruta pública.
  Los videos conservan el procesamiento previo. La escritura utiliza un archivo temporal
  que no es accesible mediante la ruta pública, antes de moverlo al nombre definitivo.
- PNG/WebP animados se conservan completos: recomprimirlos sigue pendiente de un
  codificador que preserve todos los cuadros. No afirmar compresión universal completa.
- Las plazas locales publicadas en el mismo milisegundo reciben IDs distintos. Las
  plazas inmediatamente disponibles vencen por su TTL, no al instante de publicación.
  Una prueba usaba relojes incompatibles entre usuarios; ahora comparten el mismo reloj.
- El backend SQL también conserva una plaza inmediatamente libre hasta su TTL.
  Las respuestas de reserva, cancelación y liberación incluyen la columna de distancia
  que esperaba el lector SQL; antes la reserva podía persistir y responder con error 500.
- El script de despliegue verifica la configuración segura antes de copiar archivos.
- Se recupera la prioridad `^~ /media/` ya presente en GitHub: las fotos y videos
  subidos deben llegar a la API en lugar de resolverse como archivos estáticos de Nginx.
  `sh qa/check-nginx.sh` valida la configuración y el enrutamiento de JPG, WebP,
  MP4, API y health en un contenedor aislado con upstream simulado. No prueba TLS
  ni la configuración externa de la VPS.

## Bloqueos y próximos controles

1. Ampliar la cobertura E2E: el circuito de autenticación/perfil y los rechazos de API
   ya pasan con PostGIS y Redis reales; no equivalen a certificar todos los módulos.
2. La diferencia de clases con Contabo quedó identificada y reconstruida (ver evidencia
   abajo). Antes de desplegar, verificar también dependencias externas, configuración,
   compatibilidad de clientes y transición en staging. La rama conserva las mejoras
   de matching de Trueque presentes en GitHub y sus regresiones verificadas.
3. Configurar y validar Firebase Admin de producción, dominios y métodos habilitados.
   Ensayar la transición en staging antes de desactivar el acceso actual de laboratorio.
4. Completar compresión de animaciones; comprobar todas las entradas de clientes,
   orientación, GPS/metadatos, cargas concurrentes y límites de cuerpo HTTP/memoria.
5. Ampliar E2E a feed, publicaciones, mascotas, trueque, estacionamiento, denuncias,
   perfiles ajenos, roles, enlaces, tiempo real, errores de red y doble envío.
6. Auditar accesibilidad con teclado y lectores de pantalla; validar con personas
   de 20–70 años. La captura móvil ya muestra que el formulario queda debajo de un
   bloque introductorio alto: medir éxito/tiempo de acceso antes de cambiar el diseño.
7. Verificar Android e iOS en dispositivos reales, proveedores OAuth/SMS reales en
   staging, rendimiento, restauración de backups y reversión del despliegue.
8. Autorizar lanzamiento solo con todos los recorridos críticos aprobados, sin
   defectos críticos/altos, versión desplegable identificada y recuperación ensayada.

No hay despliegue automático ni certificación de lanzamiento en este conjunto de cambios.

## Reconstrucción verificable de Contabo

`baselines/contabo-20261019.json` registra hashes de los dos JAR originales y resultados.
`baselines/contabo-20261019.patch` reconstruye el código desplegado desde el commit
`22c21ff736bd72fa2347c9ec4248adb80c0767fb`. Aplicarlo **solo a una copia aislada** de ese
commit, nunca a la rama de lanzamiento: retira exclusivamente el matching de habilidades
en SocialRepository, SocialPost, FeedCandidate, ViewerContext y los motivos/orden del feed.
La copia local usada está en `.recovery/reconcile-src`; los JAR están en la recuperación
de VPS y no se versionan. Compilar allí `:backend:compileKotlin` con el SDK indicado arriba.

La herramienta `baselines/CompareClasses.java` usa ASM 9.8 (`asm` y `asm-util` en el
classpath). Compilarla con `javac -cp "$ASM_CP" -d /tmp/ogt-class-check qa/baselines/CompareClasses.java`.
Ejecutar `java -cp "$ASM_CP:/tmp/ogt-class-check" CompareClasses ORIGINAL.jar DIRECTORIO_CLASSES`
para cada JAR y su directorio compilado (`backend/build/classes/kotlin/main` y
`shared/build/classes/kotlin/jvm/main`). Falla si falta, sobra o difiere alguna clase.
Solo excluye información de depuración y `SourceDebugExtension`; conserva Kotlin Metadata,
instrucciones, firmas, constantes SQL, bootstrap handles, frames y tablas de excepciones.
Resultado: 175/175 clases backend normalizadas iguales (174 byte a byte), y 429/429 clases
compartidas iguales byte a byte. No quedan diferencias ejecutables sin explicar en esas
clases. Esto no implica que dependencias de terceros, datos, infraestructura o despliegue
estén certificados. Contabo no fue modificado.

`baselines/runtime-libraries-20261019.json` compara por SHA-256 las 165 bibliotecas
del servidor con la distribución local: las 163 de terceros existentes coinciden
byte a byte; solo cambian `backend.jar` y `shared-jvm.jar`, como corresponde a las
correcciones preparadas. Se agregan nueve bibliotecas de procesamiento de imágenes,
también registradas con su huella. Esta comparación verifica contenido, no ausencia
de vulnerabilidades ni comportamiento en producción.

## Retiro de Facebook (19/10/2026)

Por pedido del usuario se eliminó Facebook de los botones y adaptadores de acceso
web, Android y iOS. Google, correo y teléfono conservan sus recorridos; en Compose
el botón Google ocupa el ancho disponible. Se actualizó la regla de diseño para
no reintroducir Facebook. La captura de Firebase aportada por el usuario no
mostraba ese proveedor habilitado, por lo que no se cambió la consola.

Verificación: 10/10 pruebas existentes de autenticación/perfil/recuperación en
Chromium y WebKit móvil emulado, 5/5 controles de sesión, sintaxis JavaScript y
Swift correctas, compilación Kotlin Android aprobada excluyendo
`:composeApp:processDebugGoogleServices` por faltar `google-services.json`.
No equivale a compilación iOS completa ni validación OAuth real. No se desplegó.

## Despliegue parcial autorizado (19/10/2026)

Se publicó la retirada de Facebook sobre la versión vigente de Contabo, sin
publicar aún el resto de la rama de lanzamiento. Archivos públicos `app.js`,
`auth.js` e `index.html` verificados byte a byte contra el candidato; referencias
JS versionadas para renovar caché. Evidencia y parche reproducible en
`baselines/facebook-deployment-20261019.{json,patch}`. Respaldo remoto:
`/home/iankmp/ogt-backups/facebook-20261019T184217Z`.

La comprobación posterior detectó un 502: `api` resolvía a 172.20.0.3 en la red
compartida, mientras OGT estaba en 172.31.0.4. Se reemplazaron los cinco destinos
por `ogt-api:8080`, se validó Nginx y se recargó sin reiniciar API ni bases. La
configuración local recoge la misma corrección. `/health` público volvió a
responder con `ogt-backend` y `up`. No se modificaron credenciales ni datos.

Regresión local completa posterior: **24/24 pruebas aprobadas** en Chromium y
WebKit móvil emulado. El control aislado del proxy también pasa. Estos resultados
no equivalen a E2E autenticado de producción: Firebase Admin y OAuth/SMS reales
siguen pendientes; la herramienta de navegador continúa bloqueada por su política.

## Auditoría UI y autenticación real (19/10/2026)

Se agregó `tests/ui-readiness.spec.mjs`: recorre 12 pantallas autenticadas en
Chromium y WebKit móvil emulado, conserva capturas, revisa errores JavaScript,
desbordamiento horizontal, solapamiento del encabezado y accesibilidad con axe.
La primera ejecución identificó enlaces móviles sin nombre accesible, regiones
sin distinguir y contraste insuficiente en etiquetas y opciones de publicación.
Se corrigieron y publicaron nombres/regiones/contraste con respaldo registrado
en `baselines/accessibility-deployment-20261019.json`. Las 24 pruebas funcionales
pasaron y posteriormente las dos auditorías de accesibilidad pasaron. La revisión
visual detectó además un encabezado móvil superpuesto: se agregó ajuste de filas
y una comprobación geométrica; su estado debe verificarse en la siguiente ejecución.
Los informes automáticos no sustituyen teclado, lectores ni dispositivos reales.

La credencial Firebase Admin aportada localmente fue validada sin mostrar claves,
guardada con permisos 0600 fuera del repositorio y copiada por SSH a la carpeta
privada `/home/iankmp/ogt-secrets`. Google aceptó la credencial desde Mac y Contabo.
Un contenedor temporal con la imagen desplegada verificó un ID token real de
Firebase emitido para una identidad sintética; la identidad Firebase se eliminó
al finalizar, sin escribir en la base de datos OGT. Esto no prueba OAuth/SMS UI.

Se activó Firebase Admin en el backend existente de Contabo mediante montaje de
solo lectura y se configuró `OGT_ALLOW_DEV_TOKENS=false`, `OGT_ENV=production`.
Se recreó solo API y se recargó Nginx. Respaldo de configuración con permisos 0600:
`/home/iankmp/ogt-backups/firebase-20261019T191747Z`. La salud interna pasó.
No se desplegaron aún los demás cambios de backend de la rama de lanzamiento.

Comprobación pública tras activar Firebase: `/health` y portada 200, perfil sin
sesión 401. Un token de laboratorio ya no permite acceder, pero el backend antiguo
responde 500 en vez de 401: defecto pendiente de desplegar el manejo de excepciones
ya presente en la rama. No afirmar certificación de producción.

La auditoría ampliada detectó además contraste insuficiente en metadatos del feed
sobre fondo claro. Se ajustó el gris secundario de #6b7280 a #626976; diferencia
visual intencional para mejorar legibilidad. El encabezado móvil ahora distribuye
título y controles en filas separadas. Ambos cambios requieren registrar la
validación final y desplegarse; no están en el primer parche de accesibilidad.

Nombre público acordado: **OGT** o **Only Good Things**. El ID técnico Firebase
permanece `goodthings-55612`.

Validación final de UI: las dos auditorías ampliadas pasan (Chromium y WebKit móvil), incluidas las 12 pantallas, contraste y ausencia de solapamiento del encabezado. Captura móvil de Mascotas revisada visualmente.

Se publicó el ajuste móvil y de contraste, con respaldo y hashes en `baselines/mobile-readability-deployment-20261019.json`. La hoja de estilos descargada por HTTPS coincide exactamente con el archivo preparado; salud pública 200.

Parche mínimo de autenticación desplegado: `baselines/auth401-hotfix-20261019.{json,patch}`. Se reconstruyó la base probada de Contabo y se conservaron byte por byte todas las entradas ajenas a ApplicationKt del JAR original. La imagen anterior permanece etiquetada para reversión. Contenedor temporal: salud 200, token inválido 401, anónimo 401; mismos resultados públicos tras despliegue. No hay cambios de esquema ni despliegue del resto de la rama.

Una regresión completa se interrumpió al detenerse el emulador local de autenticación (puerto 19099 sin proceso); no se contabiliza como aprobación. Se reinició el servicio y se repite la suite completa.

Resultado de la repetición completa tras recuperar el emulador: **26/26 pruebas aprobadas en 1,1 minutos**, incluyendo flujos funcionales y auditoría de 12 pantallas en Chromium y WebKit móvil emulado. Persisten pendientes de lanzamiento: OAuth/SMS de usuario real, dispositivos reales y despliegue/regresión de los restantes cambios de backend (incluida compresión de imágenes). No interpretar la sincronización de esta rama como igualdad total con producción ni con main.


## Despliegue aislado de multimedia y sesiones web (19/10/2026)

Se desplegó solo el paquete multimedia sobre la imagen de producción con el parche 401, conservando 188 entradas ajenas a multimedia del JAR byte por byte. Se incorporaron nueve bibliotecas de imagen; las bibliotecas anteriores no cambiaron. Respaldo y hashes: `baselines/media-deployment-20261019.json`. Imagen de reversión: `ogt-backend:pre-media-20261019`. Se corrigió la prioridad de `/media/` en Nginx y se validó su configuración.

Validación: 12/12 pruebas de medios (almacenamiento y HTTP) sobre la fuente reconstruida de producción; prueba aislada del proxy aprobada; contenedor candidato con salud 200, token inválido 401 y subida anónima 401. En el runtime de Contabo, JPEG de 6.135.720 bytes reducido a 1.905.840 bytes (1502×2048), con orientación correcta; WebP transparente de 44 bytes conservado sin crecer; WebP animado de 156 bytes preservado; imagen de dimensiones abusivas rechazada. Las animaciones todavía no se recomprimen. No se garantiza reducción de archivos ya optimizados.

Se comprobó por HTTPS una imagen sintética procesada por el almacenamiento de producción: 200, JPEG y coincidencia exacta de bytes. Se eliminó únicamente ese archivo sintético al terminar. Evidencia: `baselines/media-delivery-20261019.json`. Esta comprobación cubre procesamiento y entrega pública, no equivale a una subida autenticada desde navegador en producción.

La web pública todavía mostraba controles de laboratorio. Se desplegaron los cambios de sesión ya probados: eliminar el fallback a tokens de laboratorio, recuperar/renovar sesiones Firebase, permitir laboratorio solo en localhost explícito y región accesible del formulario. Evidencia: `baselines/auth-web-deployment-20261019.json`; 5/5 pruebas unitarias de autenticación aprobadas; app.js y auth.js públicos coinciden con la rama local. En navegador público se confirmó ausencia de laboratorio y rechazo de credenciales sintéticas inexistentes con «Correo o contraseña incorrectos».

El intento Google desde el navegador integrado terminó con `auth/network-request-failed` antes del selector de cuenta. Los endpoints públicos Firebase responden; el acceso por correo llega a Firebase. Se solicitó comprobar Google en Safari/Chrome para distinguir el entorno integrado de un defecto de OGT. Google y SMS continúan pendientes de aprobación E2E real.

La corrección del proxy también recuperó el video de introducción existente: HTTP 200, video/mp4, 6.111.790 bytes; no se reemplazó ni eliminó el recurso.


## Correcciones compatibles de perfil, mascotas, estacionamiento y cierre (19/10/2026)

Paquete preparado sobre la versión exacta de producción, sin sustituir shared-jvm ni activar cambios adicionales de clasificación del feed. Diferencias intencionales:
- Un perfil existente conserva el nombre editado en OGT al volver a iniciar sesión con Firebase.
- La consulta de mascotas cercanas devuelve una estructura serializable con los mismos campos.
- Un lugar de estacionamiento disponible dura hasta su vencimiento; se corrige la devolución SQL de distancia y confirmación.
- Las publicaciones de pedidos cerrados permanecen en el historial, pero dejan de incluir una referencia de pedido activo.
- Los ajustes de producción rechazan explícitamente tokens de laboratorio y emuladores.

La nueva prueba `tests/closed-need.spec.mjs` falló antes de aplicar el cambio porque devolvía un needId cerrado; pasó después en Chromium y WebKit móvil. La corrección mínima de producción está registrada en `baselines/closed-need-production-20261019.patch` (ya está incluida en el código de la rama).

Validación del paquete exacto: 16 pruebas unitarias/HTTP aprobadas; 26 pruebas E2E/UI aprobadas ejecutando `OGT_FULL_E2E=1 npm --prefix qa test -- --grep-invert 'trueque published'`. Esta selección incluye el nuevo cierre; excluye las dos ejecuciones del escenario de coincidencia de habilidades, que pertenece a cambios de feed de la rama aún no desplegados. No se oculta una falla del paquete: esa funcionalidad adicional se conserva pendiente y no se incorpora mediante este parche. El JAR probado utiliza shared-jvm de Contabo, no el de la rama con funcionalidades adicionales.

El contenedor candidato en Contabo respondió salud 200 y sesión falsa 401. También verificó un ID token real de Firebase con identidad sintética eliminada al terminar, sin escribir en la base OGT. El usuario informó que Google abre sesión desde su navegador; una verificación de solo lectura confirmó que el perfil consultado y Firebase comparten identidad, correo verificado y proveedor Google. Esto no sustituye la prueba pendiente de SMS ni de todos los dispositivos.

Despliegue completado con respaldo y hashes en `baselines/readiness-fixes-deployment-20261019.json`. Verificación pública posterior: salud 200; perfil anónimo, token falso y subida sin sesión 401. Se conservaron 155 entradas ajenas al parche y la biblioteca shared de producción.

El usuario realizará la prueba de teléfono/SMS más adelante. Permanece pendiente, no bloquea el trabajo independiente y no se registra como aprobada.

## Inicio móvil, foto de perfil y caché del feed (26/10/2026)

Cambios locales, sin despliegue: la presentación móvil muestra «Only Good Things»
antes de «RED SOLIDARIA DE COMUNIDAD», sin «(OGT)». Una splash independiente anima
las alas del PNG original y revela el punto al terminar. Recupera la sesión en
paralelo; una sesión restaurada pasa al feed, conservando los pasos de biometría,
configuración inicial e invitaciones que ya existían. Respeta reducir movimiento.

La foto elegida se sube a `/api/v1/media/upload` y se confirma con
`/api/v1/users/profile/update`; al restaurar se prioriza el perfil de OGT sobre
el del proveedor Firebase y se usa el identificador del servidor. Un error de
subida restaura la foto anterior y se muestra en Ajustes. La actualización parcial
de perfil combina las preferencias existentes en vez de reiniciarlas.

Investigación de la captura: el post `95bf0c44-ae35-4500-9e13-74066ae72c1b`,
«Necesito Albañilería. necesito levantar una pared con el vecino», existe en
Postgres con estado `REMOVED`. El CRM consulta exclusivamente `VISIBLE`. La
consulta al servidor fue de solo lectura. La imagen de ropa es el recurso local
`feed_story_donacion` utilizado cuando falta foto; no prueba que el post sea mock.
La caché podía conservar publicaciones retiradas mientras la app estaba cerrada.
El nuevo chequeo autenticado `/api/v1/social/visibility` comprueba IDs concretos;
no interpreta la ausencia en una página del ranking como eliminación. Ante fallo
de red o servidores anteriores conserva la caché y sigue intentando cargar el
feed. Los fixtures conocidos siguen excluidos de la importación de caché.

Validación local:

- 133 pruebas compartidas, 35 de backend y 5 de la app: aprobadas.
- Compilación Kotlin Android e iOS Simulator ARM64: aprobada. Para Android se
  inyectó una configuración Firebase **demo** desde un archivo temporal mediante
  un script de inicialización de Gradle, sin agregar configuración al proyecto.
  No equivale a un APK con Firebase de producción ni a una compilación firmada iOS.
- 17 comprobaciones de sesión web, branding y CRM: aprobadas.
- 8 pruebas integradas en Chromium y WebKit móvil: aprobadas. Incluyen registro,
  foto, recarga, salida/entrada, recuperación, rechazo de tokens y el nuevo caso
  `mobile-persistence.spec.mjs` de foto, preferencias y visibilidad de caché.
- La base QA detenida previamente necesitó las migraciones existentes 23–26;
  se aplicaron únicamente al proyecto Docker `ogt-qa`, sin borrar datos.
- La prueba no versionada `profile-photo.spec.mjs`, ya presente al iniciar esta
  tarea, falla en ambos navegadores: espera `data-avatar-user` y un placeholder
  que el `web/app/app.js` original no implementa. No se modificó esa prueba ni
  la web para cambiar este comportamiento ajeno al pedido móvil.
- Pendiente la comprobación visual de la animación y el ciclo completo en un
  teléfono físico. No se instaló ni publicó una nueva versión móvil.

### Instalación en el simulador local (26/10/2026)

Se compiló la app iOS completa con Xcode 26.2 y se instaló sobre OGT en el
simulador iPhone 17 Pro, UUID `B8D6D203-C781-446E-B85B-07631CE9A9FC`, sin
eliminar la app ni sus datos. La firma debe quedar habilitada incluso en el
simulador (`CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-`): deshabilitarla
impide que Firebase acceda al llavero y produce el error -34018. Recompilar con
la firma local recuperó la sesión existente, sin pedir credenciales ni borrar
el llavero.

Se comprobó visualmente la animación y, en dos aperturas, el acceso directo al
feed conservando la sesión. La foto visible es la guardada en el servidor; no
se cambió la foto ni se subió contenido durante esta verificación. Evidencias
locales en `build/ogt-ios-simulator/verified-startup.mp4`, `animated-logo.png` y
`verified-home.png`. La compilación instalada usa los cambios locales, todavía
sin publicar en GitHub. No se desplegó el backend: su nueva validación de caché
y la combinación de preferencias siguen pendientes de publicación.

### Backend móvil publicado en Contabo (26/10/2026)

Publicado `backend-mobile-20261026T202900Z-79b9b31e3acf` sobre la revisión real de
Contabo `45c994ebef187102d6cb40fda46362bba0c8f3c6`. Esa revisión contiene funciones
de CRM posteriores al HEAD local: se reconstruyó en una carpeta aislada y solo se
sustituyeron SocialRoutesKt y SocialSqlRepository, incluyendo sus clases generadas.
Las 299 entradas restantes del JAR son idénticas a las desplegadas antes. Se
mantuvieron la imagen base, todas las bibliotecas, el JAR shared, web, CRM,
configuración, credenciales, volúmenes y esquema; no se ejecutaron migraciones en
producción. El manifiesto integrado sigue identificando la base; el parche queda
registrado por separado en `~/ogt/current-backend-patch.json`.

Pasaron 50 pruebas de backend y 132 compartidas de la revisión de Contabo, además
de 8 pruebas integradas del paquete exacto en Chromium y WebKit. En producción:
salud HTTPS 200, nueva ruta anónima 401, CRM público accesible con sus controles
de acceso intactos, contenedor saludable y archivos protegidos idénticos. Desde
el iPhone 17 Pro instalado: sesión 200, comprobación de visibilidad autenticada
200 y feed 200. Se rechazó un aviso de acceso al portapapeles para continuar sin
leerlo. No se subió una foto ni se crearon usuarios o publicaciones de prueba en
producción.

Respaldo remoto con JAR anterior, imagen etiquetada y pg_dump verificado:
`~/ogt/backups/backend-mobile-20261026T202900Z-79b9b31e3acf`.
Evidencia y parche de origen en `baselines/backend-mobile-deployment-20261026.json`
y `.patch`. Solo se recreó API y se recargó nginx para resolver su dirección nueva;
las primeras sondas HTTPS durante el reinicio devolvieron 502 y las sondas finales
200. El activador incluye reversión a la imagen/JAR anterior si falla la aceptación.

### iPhone real y error de símbolos de Xcode (26/10/2026)

Ante `DVTDeviceSymbolsCoordinatorErrorDomain`, código 908,
`dyld_shared_cache_extract_dylibs failed` en IDEInstallCoreDeviceWorker, se
comprobó iPhone 17R emparejado, modo desarrollador habilitado y DDI disponible.
El dispositivo corre iOS 27.0.1 (24A446), la Mac macOS 15.7.7 y Xcode 26.2.
El disco interno tenía apenas 386 MiB libres. Se trasladó y verificó por contenido
la caché antigua `iPhone18,2 26.5.2 (23F84)` a
`/Volumes/DEV_MAC/IAN/caches/xcode-device-support/`, dejando un enlace en su ruta
original y conservando los archivos. Se omitieron los otros dos traslados al
haber espacio suficiente. La caché de navegadores QA también quedó en el disco
externo mediante enlace. El espacio libre final fue aproximadamente 7,4 GiB.

Se ejecutó `scripts/ogt-ios-wifi.sh --no-update --device 'iPhone 17R'` con Java 17,
la caché Gradle de validación y kotlin.native.home 2.3.0. Resultado: compilación
ARM64 y firma de desarrollo correctas, instalación directa por CoreDevice y
apertura de `com.onlygoodthings.app` en el iPhone real. Se confirmó el proceso
activo después del lanzamiento. Registro: `build/ogt-device-retry.log`.
No se desinstaló la app ni se borraron datos o credenciales del teléfono.

La instalación directa no adjunta LLDB y no depende del paso de extracción de
símbolos que estaba fallando en la ejecución interactiva de Xcode. El éxito de
esta instalación no certifica que se haya reparado la preparación del depurador
para esta combinación de Xcode/iOS; eso requiere una comprobación separada.

### Revisión de avatares y finalización del scroll — 2026-10-26

La validación anterior de persistencia era integración HTTP en navegadores;
no cubría la selección de una foto desde la app nativa ni todas sus superficies.
No debe presentarse como E2E completo del cambio de foto en iOS.

La consulta de producción, de solo lectura, confirmó que el autor Romorio tiene
`photo_url` y la cuenta jose de las nuevas capturas tiene ese campo nulo. Son
identidades distintas. Eso no permite determinar si un intento anterior de
subida desde jose falló: queda pendiente confirmar en qué cuenta se eligió la foto.
No se modificaron fotos ni cuentas de producción para esta comprobación.

Se encontró además una divergencia real: el feed y WebSocket actualizan la tabla
local de usuarios, pero la copia de la identidad en sesión no observaba esas
actualizaciones. `OgtPreviewStore` ahora observa `socialTick` y sincroniza solamente
la cuenta actual. Tres pruebas nativas verifican actualización por alias Firebase,
aislamiento entre cuentas/cierre de sesión y reemplazo de la vista previa local
por la URL remota. Las ocho pruebas de composeApp pasaron inicialmente en iOS.
Se recompiló e instaló el simulador conservando sus datos: con Romorio, cabecera,
Tu historia, autor y Ajustes muestran la misma foto después del reinicio.
Esto verifica visualización/restauración de una foto ya guardada; no equivale a
verificar una nueva subida desde el selector nativo con la cuenta jose.

Por pedido adicional, la barra superior ya no usa `chromeIdleMs`: conserva el
umbral para ocultarse y reaparece al recibir `onPostFling`, después de la inercia
del componente desplazable, también cuando termina un arrastre con velocidad cero.
Se mantienen la animación visual, los encabezados fijos y la exclusión de ajustes
de layout. Las pruebas de regresión cubren una pausa superior al antiguo plazo,
la inercia, finalización sin inercia, movimientos mínimos y encabezados fijos.

Resultado final: 11 pruebas de composeApp pasaron en `iosSimulatorArm64Test`
(3 de sesión, 3 de barra y 5 existentes); `git diff --check` sin errores.
Ambas compilaciones Xcode finalizaron correctamente. La última versión se instaló
y abrió en el simulador y en iPhone 17R, conservando los datos existentes.
Registros: `build/profile-scroll-native-tests.log`,
`build/profile-scroll-simulator-build.log`, `build/profile-scroll-device-install.log`.
La herramienta de control del simulador no logró generar el desplazamiento con
arrastre/rueda: no se da por verificado visualmente ese gesto. La transición de
la barra se comprobó con los callbacks nativos en las pruebas; queda por realizar
el gesto manual en el teléfono para completar su validación visual.

### Tarjeta provisional durante el arranque — 2026-10-26

Baseline: `OgtPreviewSession` importa la caché de publicaciones antes de abrir
el feed; `SocialFeedSnapshot` no incluye los perfiles de autores. FeedScreen
dibujaba inmediatamente esas tarjetas y varios efectos las volvían a pintar
antes de terminar la consulta de visibilidad y la carga remota. Por eso se podía
ver `Alguien de la comunidad` (fallback del autor) hasta hidratar su perfil, o
ver por un instante una tarjeta que el servidor retiraba durante la validación.

Cambio limitado a FeedScreen: primera lista vacía e indicador de carga hasta
finalizar el intento inicial; todos los repintados respetan esa condición.
Después se dibuja el resultado ya reconciliado. Ante fallo de red se libera
la caché disponible, sin borrarla por errores ni por ausencia en una página.
Se conserva el comportamiento de filtros, refresco, reacciones y precarga de
medios una vez terminado ese primer intento. Diferencia intencional: el feed
espera la primera carga antes de mostrar tarjetas, en lugar de pintar primero
contenido provisional de la caché.

Regresión: 133 pruebas shared JVM y 11 composeApp iOS pasaron; incluye retirada
explícita, conservación de otras páginas/borradores y persistencia al reiniciar.
Compilación del simulador correcta, instalación sin borrar datos y arranque
confirmado con autor Romorio e identidad/foto correctos. La observación visual
confirma el feed resultante; no constituye captura exhaustiva de cada frame
de arranque ni prueba nativa de pérdida de conectividad.
Registros: `build/feed-startup-tests.log`, `build/feed-startup-simulator-build.log`
y `build/feed-startup-device-install.log`.

Comprobación adicional de persistencia: se leyó exclusivamente para diagnóstico
el plist de preferencias de la app instalada en iPhone 17R. Resumen, sin contenido
personal: 19 publicaciones en caché; ni el UUID
`95bf0c44-ae35-4500-9e13-74066ae72c1b` retirado ni el texto de albañilería están
presentes. El rótulo genérico de autor por sí solo no identifica una publicación
retirada: también afecta publicaciones válidas cuyo autor no se importó al arrancar.

Se reforzó además el punto de persistencia: la validación llama ahora a
`session.reconcileFeedVisibility`, que retira los IDs confirmados y guarda
sincrónicamente el nuevo snapshot antes de la siguiente solicitud. No depende
solamente del efecto reactivo de repintado o de que la carga del feed termine bien.
Una nueva prueba nativa con preferencias reales verifica el guardado inmediato
y la restauración en una sesión nueva, preservando otra página y una publicación
pendiente. Resultado: 12 pruebas nativas y 133 shared JVM sin fallos.
Registros adicionales: `build/feed-cache-removal-tests.log` y
`build/feed-cache-removal-device-install.log`.

Se cruzaron además los 19 UUID guardados en la caché real del iPhone con Postgres
de producción mediante consulta de solo lectura: **19/19 VISIBLE**, sin filas
retiradas ni IDs faltantes. Resumen sanitizado:
`build/feed-cache-server-visibility-audit.json`. Las copias temporales locales
del plist de preferencias se eliminaron tras extraer los resúmenes; el teléfono
no se modificó durante estas lecturas. Esto distingue el fallback transitorio
del autor de una publicación eliminada: actualmente no hay publicaciones
retiradas entre las 19 entradas comprobadas de la caché del dispositivo.
La compilación final se instaló y abrió correctamente en iPhone 17R a las 18:21
(hora de Buenos Aires), sin desinstalar ni borrar sus datos.

### Ajuste visual de historias y logo — 2026-10-26

Baseline: insignia de historias de 20 dp, fondo oscuro y signo blanco; logo de
cabecera en caja de 36 dp con Fit, cuyo dibujo visible medía unos 21 dp debido
a los márgenes transparentes del PNG. El avatar de cabecera mide 44 dp.

Cambios pedidos: insignia blanca (`canvas`), sombra circular de 4 dp y signo
oscuro (`ink`), manteniendo tamaño, posición y acción de Tu historia. El logo
conserva el archivo y su proporción: el PNG de 512×512 tiene dibujo entre las
filas 106–405 (300 px); una caja de alto `iconButton` y ancho `iconButton*512/300`
con Crop retira los márgenes verticales. El dibujo visible alcanza así 44 dp,
igual que el avatar, sin aumentar la altura de la cabecera ni cambiar sus acciones.

Las 12 pruebas nativas de composeApp pasaron, incluidas las regresiones de barra,
sesión y caché. Registro: `build/story-logo-tests.log`.
Se compiló e instaló en el simulador conservando la sesión: comprobación visual
correcta del logo alineado en altura con el avatar, insignia blanca con sombra
visible y signo oscuro. Se mantuvieron las acciones de cabecera, Tu historia,
filtros y tarjetas, así como la altura de la barra. Registros de compilación e
instalación: `build/story-logo-simulator-build.log` y
`build/story-logo-device-install.log`.

Corrección de la comparación visual: la primera versión igualaba el logo con
el marco de 44 dp del avatar, pero su borde ocupa 1 dp por lado; la foto visible
es menor. La captura aportada por el usuario mostró esa diferencia. El logo
se ajustó a 42 dp de dibujo visible, conservando proporción y centrado.
En la captura del simulador de la versión corregida, la medición del dibujo
oscuro del logo y del relleno naranja del avatar da exactamente las mismas filas
283–352: 70 px de alto en ambos. Evidencia: `build/logo-visible-height-check.png`
y `build/logo-visible-height-measurements.json`. Se mantienen sin cambios el
marco de 44 dp, la altura de la barra, el tamaño y las acciones de sus botones.
Registros de esta corrección: `build/logo-visible-height-simulator-build.log`
y `build/logo-visible-height-device-install.log`.
La instalación anterior (`story-logo-device-install.log`) llegó a compilar pero
falló por pérdida de conexión con CoreDevice; no se considera instalada.
La corrección de 42 dp sí se compiló, firmó e instaló correctamente en iPhone
17R. El lanzamiento automático fue rechazado por el teléfono bloqueado
(`BSErrorCodeDescription = Locked`); requiere desbloquearlo y abrir OGT. No se
afirma comprobación visual en el teléfono para esta revisión: la medida de
70 px idénticos corresponde al simulador.

### Auditoría E2E de historias efímeras — 2026-10-26

**Resultado: no existe una implementación funcional y consistente de extremo a
extremo. Hay un visor de demostración móvil y campos de base de datos sin el
circuito de creación, consulta y vencimiento que los conecte.** Esta auditoría
no acredita una implementación aprobada ni sustituye las pruebas de aceptación
de una función futura. Se analizaron historias efímeras del riel superior, no
las anécdotas de homenajes llamadas `story` en algunos módulos web/pruebas.

Cobertura y evidencia:

- iOS Simulator: arranque con sesión existente, pulsación de Tu historia, visor
  y retorno automático al feed. Se observó una foto de demostración de una
  persona distinta al usuario, bajo `Vos / Ahora`, y el texto fijo
  `Un recorte de lo bueno que viste o hiciste hoy.` No se abre editor ni picker.
- Código compartido Android/iOS, contratos, persistencia, servidor, esquema y
  código del CRM del baseline realmente desplegado. No se ejecutó Android ni
  un recorrido administrativo real de historias: no hay filas de historias ni
  una función completa disponible para ello.
- Producción, solo lectura: 19 publicaciones VISIBLE y 17 REMOVED; todas tienen
  `is_story=false` y `story_expires_at=NULL`. Cero historias almacenadas.
- API local aislada `ogt-qa`, Firebase Auth Emulator y el mismo backend.jar que
  producción: SHA-256 `79b9b31e3acf8a483f2ea739265945a3672671902e696e6810edbf36d9d0f961`.
  El archivo desplegado se comparó por hash. No se crearon ni editaron usuarios,
  publicaciones, medios o historias en producción.

Hallazgos que impiden cerrar el recorrido:

| Tramo | Evidencia y efecto |
| --- | --- |
| Crear | FeedScreen abre StoryViewer desde Tu historia. No existe editor, selección, subida ni confirmación de publicación de historias. |
| Identidad/contenido | `storiesFrom` crea siempre `story-me`, autor `Vos`, tiempo `Ahora` e imagen local `feed_story_me`. Atribuye a la sesión contenido de demostración que no publicó. |
| Obtener historias | `followedNeighbors()` devuelve una lista vacía. El riel se construye con `remember` sin observar historias remotas. El feed del servidor excluye `is_story=true`; no hay una consulta de historias conectada al riel. |
| Publicar | El endpoint social/publish no lee `isStory` ni vencimiento. La prueba envió ambos: se guardó una publicación normal, `is_story=false`, sin vencimiento. |
| Contrato/vencimiento | SQL tiene `story_expires_at`, pero SocialPost no transporta esa fecha y el código de ejecución no la usa. Una fila vencida preparada exclusivamente en QA sigue apareciendo en posts-by-author y pasa social/visibility. |
| Visor | Su modelo admite un DrawableResource local, no una historia remota ni reproducción de video. Avanza a los 5,2 segundos fijos; las barras son estáticas. No tiene pausa mantenida ni coordinación explícita con el ciclo de vida. |
| Vistas/persistencia | `seenStoryIds` es estado remember del feed, sin almacenamiento duradero ni confirmación al servidor. No representa vistas compartidas entre dispositivos. |
| CRM | Puede moderar publicaciones mediante sus controles genéricos, pero su consulta no transporta el tipo de historia ni su fecha de vencimiento, ni excluye historias vencidas. No hay circuito administrativo específico validado para el riel. |
| Medios | La subida genérica existe y está autenticada. El archivo se sirve por URL pública con caché immutable de un año; la prueba confirmó HTTP 200 aun después del vencimiento preparado en QA. Antes de reutilizarlo hay que definir si vencer significa solo dejar de listar o también revocar acceso al archivo. |
| Pruebas | Las pruebas existentes que mencionan stories suelen cubrir anécdotas. No prueban crear una historia efímera en la app, verla desde otra cuenta, reiniciar, vencerla y retirarla desde Control. |

Prueba reproducible: con los servicios QA y el backend local en ejecución,
`node qa/scripts/audit-stories.mjs`. Es un diagnóstico de carencias, no un test
que declare aprobada la función. El script restringe sus endpoints a localhost
y sus consultas SQL al proyecto Docker `ogt-qa`; crea sus propios datos de prueba.
Prepara una fila futura/vencida únicamente en esa base y termina retirando su
publicación con la API. Resultado: `qa/artifacts/stories-audit.json`.
Controles adyacentes comprobados: publicar sin autenticación devuelve 401;
la eliminación del autor invalida la visibilidad genérica de la publicación.

Para aceptar una implementación futura debe cerrarse el mismo circuito entre
app, API, base, caché y CRM: crear con cámara/galería (incluida cancelación y
permiso denegado), subir con errores/reintentos sin duplicar, confirmar identidad
y guardado real, leer desde otra cuenta según la audiencia definida, restaurar
tras reiniciar/cambiar de cuenta, reproducir los formatos admitidos y controlar
pausa/ciclo de vida, aplicar vencimiento con reloj del servidor, y retirar por
autor/moderación con invalidación de caché. La duración y audiencia deben quedar
definidas en el contrato, sin convertir silenciosamente publicaciones normales
en historias ni borrar medios compartidos con otras publicaciones.

En esta solicitud se añadió la prueba de diagnóstico y este registro. No se
modificó comportamiento de la aplicación, esquema ni despliegues de producción.

### Historias reales: implementación y validación (2026-10-26)

El diagnóstico anterior describe el estado previo y queda como referencia histórica. El carrusel ahora consume historias reales; el botón `+` abre cámara/galería y un editor con audiencia Comunidad/Seguidores. La foto permanece 5 segundos en el visor; el video admite hasta 30 segundos y ambos tienen un máximo de 8 MB. iOS prepara videos MP4 antes de subirlos. Android valida duración y tamaño; un archivo demasiado grande se rechaza con una explicación.

La API guarda historias separadas de las publicaciones normales mediante `story_details`, con vencimiento de 24 horas calculado por PostgreSQL. La audiencia también respeta la privacidad existente del perfil. Los archivos viven en un directorio privado, con enlaces firmados breves, comprobación de permisos en cada lectura y `no-store`. Vencimiento, retirada y pérdida de seguimiento invalidan el acceso. La limpieza borra archivos una hora después del vencimiento; quedan metadatos para moderación. No se agregaron historias de demostración en producción.

El cliente mantiene historias solamente en memoria, verifica periódicamente su vigencia y recibe una invalidación específica por socket sin difundir contenido privado. Los borradores se guardan por cuenta y conservan el identificador y el archivo preparado para reintentar sin duplicar una publicación. Si el sistema operativo eliminó el archivo temporal, el editor solicita volver a elegirlo. La publicación continúa al cerrar el editor mientras la app sigue abierta. El visor pausa al mantener presionado o salir al fondo y registra las vistas en el servidor. El autor puede retirar sus historias y consultar vistas; otro usuario no puede retirarlas.

El CRM tiene una sección Historias con búsqueda, estado, audiencia, fechas y retirada con motivo. Conserva la validación de segundo factor, los roles autorizados y la auditoría. Las historias no participan en búsquedas/listados, caché o herramientas de edición de publicaciones normales. La restricción existente de cuentas bloqueadas para publicar también se aplica a historias, conservando la lectura.

Validación ejecutada:

- 52 pruebas del backend sobre la base de producción, 134 de shared y 12 del cliente iOS, sin fallos. Compilación Android correcta. El backend local también conserva sus 37 pruebas.
- `node qa/scripts/stories-e2e.mjs`: 11 grupos de verificaciones reales entre tres cuentas Firebase del emulador: reintentos concurrentes, TTL, archivos, permisos, audiencia, privacidad, vistas únicas, separación del feed, vencimiento, moderación/auditoría y bloqueo de publicación. Resultado: `qa/artifacts/stories-e2e.json`.
- CRM y persistencia: 4 pruebas Chromium/WebKit; registro, foto de perfil, reapertura, recuperación de acceso y autenticación: 6 pruebas adicionales. Ejecutadas contra el paquete backend final y el frontend basado en producción. Se corrigió una aserción de QA que confundía el puerto de la API con el del proxy local: compara la ruta exacta del archivo y además comprueba que la imagen terminó de cargar y tiene dimensiones reales.
- Simulador iPhone 17 Pro, iOS 26.2: publicación desde el selector nativo, almacenamiento comprobado desde una segunda cuenta, reproducción de video real de 2 segundos, secuencia foto/video, pausa durante una pulsación de 6 segundos, vistas persistidas y restauración tras reiniciar. La inspección detectó y permitió corregir transparencias del visor de video en iOS.
- App habitual compilada, instalada y abierta en el simulador y en iPhone 17R sin desinstalar ni borrar datos. La cámara física y el funcionamiento completo en Android requieren una prueba manual en esos dispositivos; no se presentan como pruebas ejecutadas.

Despliegue: `stories-20261026T234702Z`. Se extendió la base que estaba desplegada, conservando las funciones más recientes de moderación/listado de usuarios del CRM. Se preservaron byte por byte 268 entradas ajenas al cambio del jar backend y 443 del shared; se comprobaron los demás archivos web, dependencias, configuración, restricciones de acceso y credenciales sin modificarlos. Solo se aplicó la migración aditiva `29_stories.sql`. Hay copia de la base, jars, CRM e imagen previa en el respaldo de esta versión. El paquete y guardas reproducibles de esta sesión están en `build/stories-release/`; no se hizo commit ni push a GitHub.

Resultados del despliegue: `qa/artifacts/stories-deployment.json`. Las pruebas de laboratorio no usan cuentas o historias de producción. El modo QA nativo está limitado al bundle separado `com.onlygoodthings.app.qa`, compilación DEBUG y una bandera explícita; el bundle habitual mantiene Firebase y API de producción.

### Tarjetas de estacionamiento sin texto recortado (2026-10-26)

Se retiraron la altura fija del título y el límite de dos líneas. Las tarjetas usan la altura natural del texto y comparten la mayor altura cuando están en fila. La disposición se decide midiendo las palabras reales, con el idioma y la escala de fuente actuales: cuando no caben completas en tres columnas, se muestran tarjetas horizontales de ancho completo. La medición incluye los títulos de búsqueda normal/en curso para evitar saltos durante la búsqueda. Al ampliar el mapa se conserva la fila compacta de iconos; el texto termina de desvanecerse antes de reducir su espacio. Los selectores de ubicación comparten la corrección y conservan sus acciones y estados.

La opción Buscar estacionamiento usa el nuevo `parking_action_search_transparent.png`, editado con imagegen para extraer el fondo y con canal alfa verificado. El original se conserva para sus otros usos.

Verificación nativa en iPhone 17 Pro/iOS 26.2: tamaños de letra estándar, extra-small y máximo de accesibilidad; textos completos; búsqueda sin cambio de disposición; estado deshabilitado; selección Por dirección y regreso sin guardar datos; mapa ampliado con fila compacta. Se restauró la preferencia de texto original (`large`). Evidencia: `qa/artifacts/parking-actions-layout.json` y capturas de accesibilidad. Compilaciones Android/iOS correctas y 12 pruebas del cliente sin fallos. La compilación física final está firmada en `build/ogt-ios-device/Build/Products/Debug-iphoneos/iosApp.app`; la instalación no se pudo completar por pérdida de conexión con el iPhone. No se modificaron backend, publicaciones, vehículos ni estacionamientos del servidor.

### Tipeo fluido y borradores de historias (2026-10-27)

Diagnóstico: `StoriesController.updateDraft` decodificaba y serializaba el mapa de borradores y el setter iOS ejecutaba `NSUserDefaults.synchronize()` por cada letra, en el hilo de UI. Además, la carga/decodificación de contactos de las publicaciones se hacía en ese mismo hilo. Ahora hay un escritor de borradores serial en segundo plano con snapshots inmutables, agrupación de cambios pendientes, errores observables y barrera de persistencia antes de subir medios preparados. El cierre drena cambios pendientes; no se introdujo un debounce que retrase artificialmente el guardado.

Los campos compartidos conservan `TextFieldValue` local, incluyendo selección y composición, y notifican el texto inmediatamente al formulario. Se mantienen la API, estilo, límites, validaciones, teclas de envío y actualizaciones externas. Los nombres se normalizan al confirmar, evitando reescribirlos en cada tecla. Los teclados de prosa usan capitalización de oraciones; contraseñas, identificadores, teléfonos, códigos y patentes conservan reglas propias. El editor de historias actualiza caption y audiencia usando el borrador más reciente. Las búsquedas de ubicación respetan cancelación y una respuesta anterior de GPS/mapa no reemplaza texto escrito después de iniciar esa petición.

Verificación: 49 entradas lógicas de las pantallas de historias, registro/acceso/recuperación/OTP, autos/estacionamiento, ayuda, mascotas/adopción/postulación, publicación, menciones, comentarios/anécdotas y sus ediciones, invitación, reporte, ajustes, mapa y chat. Se escribieron textos carácter por carácter con CUA nativo, sin pegarlos. Se usó una copia de ensayo con pantallas/callbacks reales y fixtures locales separados; posiciones iniciales de scroll y estados de diálogo permiten alcanzar todos los campos. No se publicaron ni enviaron datos. Dos campos con descripción accesible estática se verificaron visualmente. Los intentos fallidos por distribución del teclado del automatizador, recorte o selección incompleta se documentan, junto a las repeticiones válidas, en `qa/artifacts/typing-native-20261027.json`.

Se comprobó inserción/borrado con el cursor, mayúscula inicial en teclado de pantalla, cambio de audiencia sin perder el último texto, persistencia del caption/audiencia en NSUserDefaults y recuperación después de terminar y relanzar la app. Cinco nuevas pruebas comprueban escritura no bloqueante, orden/cuentas/eliminación, cierre, persistencia de video preparado y reintento ante fallo de almacenamiento. Pasaron 17 pruebas del cliente iOS, 134 de shared y compilación Android. Las compilaciones nativas de ensayo y física finalizaron correctamente. El código de producción no incluye los fixtures ni rutas de pantallas de esta copia de ensayo.

La app normal se instaló en iPhone 17R sin desinstalar ni borrar sus datos. El lanzamiento automático fue rechazado porque el teléfono estaba bloqueado; no se presenta como prueba física de tipeo. El usuario puede desbloquearlo y abrir Only Good Things. No se recibió todavía el nuevo log iOS anunciado; no se afirma haberlo analizado. No hubo despliegue backend ni commit/push en esta tarea.

La app normal final también se compiló, instaló y abrió en el simulador: restauró la sesión y entró directamente al feed. Se retiró del simulador la app separada de ensayo al terminar.

## Recorte y compresión de videos — 2026-10-27

El selector prepara los videos antes de incorporarlos al formulario y permite volver a recortar desde el original. iOS utiliza el editor del sistema cuando está disponible y una alternativa con AVPlayer, tira de fotogramas y dos controles nativos cuando no lo está; Android utiliza una vista previa y dos extremos con Media3. Cancelar conserva el original. Se conservan audio, orientación y proporciones. Las historias mantienen 30 segundos/8 MiB; las publicaciones mantienen 8 MiB sin introducirles un límite de 30 segundos. Los MP4 H.264/AAC que ya cumplen las condiciones evitan una recodificación innecesaria. Los demás se comprimen, con comprobación del archivo final y reintentos de menor resolución cuando hace falta. El contenedor Android cierra ambas pistas en el extremo elegido para evitar que el relleno AAC convierta 30 s en 30,04 s.

Cambio necesario en las publicaciones normales: antes se enviaban URLs `file://` y se anunciaba éxito aun si el servidor fallaba. Ahora se suben los archivos y sus portadas, se publica con URLs del servidor y se utiliza el identificador confirmado. Se conservan participantes, homenajes, menciones, ubicación y avisos; se completan después de confirmar. El formulario conserva sus entradas ante errores y no permite publicar mientras prepara/sube archivos. No se añadió música.

Del log adjunto se corrigieron la consulta global de servicios de ubicación en el hilo principal, la precarga de animales antes de autenticar y la exposición de parámetros de URLs firmadas en el registro HTTP. Los mensajes informativos de Firebase y las restricciones internas del teclado no se trataron como fallos de publicación sin evidencia. El fragmento entregado no contiene una petición fallida de `stories/publish`.

Validación: 27 pruebas del cliente y 137 de shared, compilación Android y compilaciones nativas iOS para simulador y dispositivo. En copias de ensayo aisladas se exportaron ocho casos: 30 s con audio, video girado, video vertical sin audio y publicación de 42 s, en ambas plataformas. Se verificaron tamaño, duración, orientación y pista audible de 880 Hz. `qa/scripts/video-upload-e2e.mjs` recibe un manifiesto de estos archivos y comprueba contra el backend/Firebase emulado local la subida, publicación, descarga con hash idéntico, portada, lectura posterior, aislamiento entre historias/publicaciones y rechazo de los 42 s como historia. También comprueba la subida de imágenes existente. Evidencia en `qa/artifacts/video-editor/{file-validation,upload-e2e,android-engine-results}.json`; no hubo escrituras de ensayo en producción.

La interfaz iOS se comprobó en el simulador: vista previa vertical/rotada, extremos iniciales, cancelación, selección mixta desde PHPicker y exportación. El gesto de arrastre continuo no pudo verificarse con el automatizador; el cálculo y los límites de ambos extremos sí tienen pruebas. Android se validó ejecutando el motor real en Pixel 6a emulado; su ventana Qt no estuvo disponible para CUA, por lo que no se afirma verificación visual de su interfaz. La configuración Firebase usada para compilar el APK de comprobación es de ensayo, no un APK para distribución.

La app normal se compiló, instaló y abrió en el iPhone 17R, conservando sus datos. Esto confirma instalación y lanzamiento; no sustituye una comprobación visual del recortador en el teléfono físico. No hizo falta cambiar/desplegar backend para esta función y no se hizo commit/push.


### Progreso y recuperación al preparar video (2026-10-27)

La preparación informa etapa, tiempo transcurrido y porcentaje real del exportador nativo (AVFoundation/Media3). Lectura de metadatos, comprobaciones y portada tienen estado indeterminado; los reintentos de compresión se identifican explícitamente. No se estima un porcentaje ficticio. La cancelación iOS resuelve una sola vez la espera aunque AVFoundation omita su callback; tokens de generación descartan resultados tardíos en ambas plataformas. Solo se limpian temporales propios, conservando el original.

El control de espera detecta 90 segundos sin avance y un máximo de diez minutos de preparación activa. Contempla suspensión en segundo plano. En iOS ofrece volver a recortar o cancelar; en Android conserva el editor con el mensaje de recuperación. La captura aportada y dos minutos de espera no permiten confirmar la causa concreta del video del usuario.

Regresión: 33 pruebas del cliente sin fallos, compilación Android y compilaciones iOS para dispositivo/simulador. En iOS se observó el porcentaje real durante la exportación, finalización de historia de 30 s y publicación de 42 s, cancelación durante compresión y vuelta al formulario. Una copia QA separada simula un exportador sin callback, con umbral reducido solo allí a dos segundos: se observó el aviso de bloqueo, regreso al editor original y cancelación. Las banderas y logs de inyección no existen en el código distribuido.

Media3 ejecutó cuatro exportaciones reales (historia con audio, giro, sin audio, publicación larga), capturando avances de 0 a 99 % y sin errores. Se repitió la comprobación de duración, pistas de audio y límite de 8 MiB sobre las salidas; los tamaños Android coinciden con el ensayo anterior. La ventana Android sigue sin estar disponible para CUA: se verifica el motor y la compilación, sin afirmar inspección visual de esa interfaz. Evidencia: `qa/artifacts/video-editor/{android-progress-results,progress-file-validation}.json`. La app habitual se instaló en el iPhone conservando datos. El lanzamiento remoto de esta actualización fue rechazado porque el dispositivo estaba bloqueado; debe abrirse manualmente tras desbloquearlo. No se cambiaron servicios ni se hizo commit/push.


### Lectura de video, importación y sesiones nativas iOS (2026-10-27)

La prueba en el iPhone mostró un caso que la exportación aislada anterior no cubría: el registro tuvo dos lecturas/preparaciones simultáneas que completaban los metadatos mientras la pantalla seguía en su estado inicial. Se añade una instancia única del host y fases explícitas (edición, copia, preparación, recuperación), rechazo de callbacks de guardado/preparación repetidos y protección de callbacks de sesiones anteriores. El cierre del editor contempla tanto una transición en curso como un controlador que UIKit ya cerró. El recorte temporal se copia a almacenamiento propio antes de liberar el editor; nunca se borra el original seleccionado.

El contador usa un Timer del run loop principal en modo common y no depende del ejecutor de compresión. Las lecturas de AVAsset tienen una compuerta de resultado único y cancelación explícita; las llamadas de cancelación del framework se realizan fuera del hilo de UI. Los registros de diagnóstico incluyen fase/sesión o dominio/código de error, sin rutas ni contenido del usuario. La importación muestra un indicador con texto en historias y en la tira compartida de publicación, y deja de generar una miniatura de video redundante antes de abrir el editor. Las imágenes y los contratos de subida/duración/tamaño permanecen iguales.

Verificación: 33 pruebas del cliente sin fallos y compilación Android; builds Swift iOS para dispositivo y simulador. Con el archivo real se exportaron 29,05 s con audio por debajo de 8 MiB. Una inyección QA de lectura sin respuesta mostró un reloj que avanzaba, recuperación y cancelación activa conservando el original. La copia QA recibió dos callbacks de guardado nativo y dos peticiones de preparación: el registro muestra un solo inicio, un resultado válido y rechazo de ambos duplicados; la pantalla se cerró al terminar. El caso de publicación de 42 s también volvió al formulario. Las banderas de inyección solo existen en el proyecto de laboratorio ignorado, no en el código instalado.

La app se instaló y arrancó directamente en el iPhone 17R con sesión conservada y respuestas HTTP correctas. El usuario confirmó el recorrido real en el iPhone: la preparación termina y vuelve al formulario. En dos intentos, el registro del dispositivo mostró un callback nativo de guardado duplicado descartado y una sola sesión de preparación; las salidas fueron de 6.312.889 y 6.366.520 bytes. También se verificó recuperar tras una lectura sin respuesta, volver a editar el original y cancelar sin bloquear la siguiente sesión. Evidencia: qa/artifacts/video-editor/reading-session-validation.json; registros locales en build/video-double-final-events.log y build/video-session-final-console.log. No hubo publicaciones de ensayo en producción, cambios de backend ni commit/push.


### Sonido, duración final y vista previa (2026-10-27)

Se añade «Quitar sonido» a historias y publicaciones con video. El archivo preparado pierde todas las pistas de audio; no es un silencio aplicado únicamente al reproductor. Se conserva en caché la copia audible del mismo recorte para restaurarla antes de publicar. El original seleccionado no cambia. Al volver a editar un video silenciado, se conserva la elección y se guarda una nueva copia audible correspondiente al nuevo recorte. Los videos naturalmente silenciosos lo informan sin ofrecer una restauración inexistente. Los borradores de historias conservan ambos campos y los borradores anteriores mantienen valores compatibles.

La acción «Recortar video» pasa a «Editar video»: permite reajustar el fragmento sin seleccionar el archivo otra vez. Se muestra la duración real del archivo final y «Vista previa» reproduce ese mismo archivo, con pausa, repetición y cierre. La reproducción se pausa en segundo plano; los controles pueden desplazarse cuando falta altura. En publicaciones con varios videos, las opciones Video 1/Video 2 permiten elegir cuál editar sin cambiar la portada ni el orden. Preparación, límites, compresión, imágenes, portada y contratos del servidor se conservan; se bloquea publicar durante el cambio y se libera ese estado al cerrar el componente.

Verificación automática: 40 pruebas del cliente y 137 de shared, sin fallos, incluyendo restauración del recorte correcto, persistencia del borrador, compatibilidad de datos antiguos y envío exclusivo del archivo silencioso. Motores nativos de ambas plataformas: archivos con audio, sin audio, giro y publicación de 42 s. Se comprobó ausencia de pistas de audio, identidad de las muestras de video antes/después del silencio, orientación, duración y límite de 8 MiB. Evidencia: qa/artifacts/video-sound/{android-validation,ios-validation}.json. En el simulador iOS se verificaron duración, silencio/restauración, reedición conservando silencio y vista previa con pausa, repetición, fin y cierre. La ventana del emulador Android no está disponible para CUA; su motor sí se probó, no se afirma inspección visual Android. No se hicieron publicaciones de ensayo en producción ni cambios de backend.

Comprobación final adicional: compilación QA iOS completa con la conservación de metadatos aplicada en AVAssetExportSession. Un clip girado de 6 s produjo una salida de 6.377.505 bytes, sin audio y con hash de muestras de video, giro de 90° y duración idénticos a su copia audible; evidencia en qa/artifacts/video-sound/ios-final-native-validation.json. La versión final de la vista previa se abrió, reprodujo y cerró en el simulador conservando el silencio elegido. La rotación del dispositivo mantiene la orientación bloqueada existente de la app; no se añadió soporte horizontal.

Cierre de esta entrega: compilación final Android correcta (video-sound-final-android.log), compilación completa iOS para dispositivo correcta (video-sound-final-device.log) y compilación completa QA para simulador correcta (video-sound-final-qa.log). La app normal se actualizó sin desinstalar en el iPhone 17R y devicectl confirmó su lanzamiento directo, sin depurador; registros video-sound-iphone-{install,launch}.log. Esto verifica instalación/lanzamiento; la prueba visual final de estas herramientas se hizo en simulador. No hubo commit/push ni despliegue de backend. La demora del empaquetado se localizó en lecturas/firma del disco externo y en una reutilización fallida de la caché de configuración; el reintento final usó configuración sin caché.


### Nueva historia y recuperación de borrador (2026-10-27)

El botón «+» abría automáticamente el borrador guardado y presentaba sus datos al intentar iniciar otra historia. Ahora abre la selección para una historia nueva. «Continuar borrador» solo se muestra si existe un borrador y permite recuperarlo explícitamente. El selector informa que elegir otro archivo reemplaza ese borrador; cancelar el diálogo, la galería o el editor nativo conserva el pendiente. La creación existente genera un ID nuevo y valores iniciales de texto/audiencia/sonido desde el archivo elegido; no copia los de la historia anterior. El cierre correcto de publicación sigue limpiando el borrador y la actualización del servidor reconcilia una publicación confirmada tras una respuesta perdida. Si hay una publicación en curso, «+» permite ver su estado y no reemplaza su archivo.

Cambio acotado a StoriesRail: se conservan visualización de historias existentes, publicación, borradores por cuenta, recorte, sonido y vista previa. Verificación: compilación Android y 40 pruebas iOS del cliente sin fallos (build/story-new-entry-validation.log), incluyendo persistencia, eliminación y aislamiento de borradores. Revisión de los callbacks nativos del selector: cancelar no entrega un archivo previo. No se afirma una nueva prueba visual completa de publicación en producción.

La compilación completa para dispositivo terminó correctamente. La corrección se instaló y abrió directamente en el iPhone 17R conservando datos; registros build/story-new-entry-{device,iphone-install,iphone-launch}.log. Sin despliegue de backend ni commit/push.


### Encuadre de historias entre móvil y web (2026-10-27)

Las historias nuevas incluyen StoryFrame: modo FILL/FIT, zoom 1–6 y desplazamientos normalizados -1..1 sobre el espacio recortable. Un marco canónico 9:16 mantiene el encuadre al cambiar el tamaño de pantalla, sin estirar los píxeles. El original y la copia preparada no se recortan espacialmente ni se recodifican para aplicar zoom. El encuadre se conserva en el borrador y se envía con la publicación. La migración aditiva 30_story_frame.sql guarda sus cuatro campos; NULL conserva la presentación anterior de las historias publicadas y los borradores anteriores. La huella de idempotencia incluye el encuadre cuando existe, manteniendo la huella antigua para peticiones sin esos campos.

El editor ofrece gestos de ampliación/desplazamiento, slider con zoom numérico, flechas accesibles, «Llenar pantalla», «Mostrar completa», reproducción del video y confirmación/cancelación. Solo «Usar encuadre» modifica el borrador. Las fotos se decodifican con orientación EXIF corregida para coincidir con la normalización existente del servidor. La vista previa de video utiliza el mismo encuadre. Se conservan recorte temporal, audio/silencio, duración, preparación, límites y audiencia. Las historias/borradores sin encuadre mantienen sus dimensiones de presentación previas; al elegir encuadrarlos explícitamente adoptan el marco nuevo.

La web pública incorpora lectura de historias efímeras agrupadas por autor y avance entre fotos/videos, sin mezclarlas con las anécdotas o las publicaciones normales. El visor y el CRM comparten story-frame.js. En historias nuevas el marco vertical se centra y los controles quedan fuera del contenido recortado o superpuestos sobre el marco; cambiar el tamaño no cambia el recorte. El CRM mantiene la presentación y los controles nativos anteriores para historias sin encuadre. El visor web usa URLs privadas renovadas al abrir, no persiste historias en almacenamiento público, pausa en segundo plano y libera medios al cerrar. No se agregó creación web de historias.

Validación: 42 pruebas del cliente iOS, 142 del dominio compartido, 37 del backend, 48 de autenticación/CRM web y 5 de geometría web, sin fallos; compilaciones Android e iOS. La nueva prueba EXIF verifica una foto sintética con orientación 6. Se probaron persistencia del borrador, FIT/FILL, escala proporcional, extremos de desplazamiento sin huecos, parámetros inválidos y compatibilidad de datos antiguos. La API se probó con fotos y videos, recuperación de encuadre, archivo sin cambios por encuadre, reintentos, rechazo de un encuadre diferente con el mismo ID y campos por defecto omitidos por el cliente móvil. Las once regresiones de historias (privacidad, seguidores, vencimiento, revocación, vistas únicas, moderación CRM, separación del feed y bloqueos) pasaron tanto sobre el backend local como sobre el paquete superpuesto al backend de producción. Evidencia: qa/artifacts/story-frame-{e2e,regression-e2e,overlay-regression-e2e,deployment}.json.

En CUA se verificaron zoom por slider, desplazamiento por botones, confirmación, cancelación que conserva el encuadre anterior y video girado con el encuadre aplicado en iOS. Se verificaron foto, cambio de tamaño del marco, secuencia y video girado con controles visibles en la web. El automatizador no permite comprobar un gesto multitáctil real en el teléfono físico; Android se compiló y comparte la geometría probada, pero no se afirma inspección visual de su reproductor. Evidencia visual: qa/artifacts/story-frame/web-preview.png. No se utilizaron fotos, videos ni historias de producción para las pruebas.

Despliegue: story-frame-20261027T165902Z. Se partió de los JAR recién obtenidos de producción y se conservaron byte por byte 346 entradas backend y 449 shared ajenas al cambio; se preservaron entorno y montajes. Solo se sustituyeron los archivos web enumerados en el manifiesto y se aplicó la migración aditiva. Copia privada de la base e imagen anterior en /home/iankmp/ogt-backups/story-frame-20261027T165902Z. Comparación posterior contra la copia SQL confirmó que las cuatro filas previas de story_details no cambiaron. Salud pública 200 y hashes HTTPS de los ocho archivos web coincidentes. No hubo publicaciones de prueba en producción ni commit/push. El paquete reproducible está en build/story-frame-release/; no debe desplegarse todo el backend local porque su base contiene versiones anteriores de funciones ajenas a esta tarea.

La primera versión de encuadre se instaló sin desinstalar en el iPhone 17R. El lanzamiento inicial fue rechazado por bloqueo; tras desbloquearlo el usuario, devicectl confirmó su apertura sin depurador. Después se compiló correctamente el ajuste final que conserva exactamente el contenedor anterior de historias sin encuadre (build/story-frame-device-final.log). Esa última instalación queda pendiente porque el usuario confirmó que está creando una historia; no se debe interrumpir esa edición. El backend y los archivos web ya incluyen el ajuste de compatibilidad.


### Encuadre directo de historias — 2026-10-27

La selección nueva permite pellizcar y arrastrar directamente sobre el contenido; cada gesto completo guarda el encuadre una sola vez, conservando texto, audiencia y archivo. Se mantienen los controles alternativos accesibles en «Más opciones de encuadre». El marco principal ahora ocupa el ancho completo, con acceso fijo a «Detalles» y publicación superpuesta; ya no requiere confirmar un editor adicional. La captura de gestos contempla el movimiento final al levantar los dedos y evita la interceptación del reproductor nativo iOS.

La vista previa de historias con encuadre también ocupa el ancho completo, con controles superpuestos: reservar altura para los botones estaba creando márgenes laterales en videos verticales. Las publicaciones normales y las historias antiguas sin encuadre conservan su presentación anterior. No cambian el contrato del servidor/web, el recorte temporal, audio, duración ni los archivos originales.

Validación final: 49 pruebas del cliente iOS sin fallos y compilación Android correcta (build/story-direct-frame-tests.log); compilación firmada para iPhone correcta (build/story-direct-frame-device-final.log). En el simulador se usó el StoryEditor real con un video sintético 360×780: arrastre directo guardado en las preferencias del borrador, texto y archivo conservados, detalles accesibles, vista previa sin márgenes laterales, reproducción/repetición y regreso con duración y texto conservados. Evidencia: qa/artifacts/story-direct-frame/validation.json y preview.png. El gesto físico multitáctil no se automatizó; su geometría y eventos finales tienen pruebas unitarias. No se crearon historias en producción.

Con la autorización del usuario de actualizar desde el inicio, la versión final quedó instalada sin desinstalar en el iPhone 17R (build/story-direct-frame-iphone-install.log). El lanzamiento automático fue rechazado porque el teléfono estaba bloqueado (build/story-direct-frame-iphone-launch.log); el usuario puede abrirla desde el ícono. Esta instalación reemplaza los pendientes indicados arriba. No hubo despliegue adicional de backend/web ni commit/push.


### Libros de historias, caché y progreso total — 2026-10-27

Cada autor es un libro y sus historias siguen ordenadas por creación. Una única barra suma la duración real de sus videos y cinco segundos por foto; al avanzar de autor comienza otro total. Carga, segundo plano, pausa y transición no se contabilizan como tiempo de foto. El indicador «Página X de Y» acompaña a la barra. El cambio de página usa perspectiva y sombra durante 520 ms; el marco exterior recorta la transformación para no invadir el área segura. Se mantienen encuadre, texto, cierre, navegación, pausa por pulsación, vistas, eliminación propia y tratamiento de historias antiguas.

Los medios móviles se conservan en el directorio privado de caché del sistema, con índice por cuenta, límite LRU de 128 MB, escritura atómica y limpieza de archivos huérfanos. La lista de permisos sigue viniendo del servidor: una copia local nunca autoriza por sí sola una historia. Se purgan elementos retirados/vencidos tras verificar la lista y se limpia al cerrar sesión/cambiar de cuenta. Los archivos firmados se identifican sin su firma temporal, evitando descargas repetidas cuando rota la URL. Solo se precarga la página siguiente. Las descargas comparten trabajo fuera del candado del índice, sobreviven al cambio de página y no bloquean una página ya guardada. El tiempo máximo de descarga se convierte en error con reintento; un reintento de reproducción invalida el archivo afectado. No se alteran archivos originales ni borradores.

La web conserva medios como blobs privados durante la vida del visor/lista, con el mismo presupuesto, caducidad, revocación y precarga; al desmontarse libera URLs y descargas. Mantiene comprobación de permisos al abrir. También suma por autor y muestra el paso de hoja; respeta la preferencia de movimiento reducido. Los controles de reproducción, volumen y pantalla completa siguen presentes. En este visor la barra individual de búsqueda del video se sustituye por la única barra total solicitada; el CRM conserva su barra de búsqueda anterior. No se cambian contratos del backend, base de datos ni creación de historias. La web está actualizada localmente y no se publicó en este turno.

Validación final: 56 pruebas del cliente iOS sin fallos, compilación Android correcta y nueve pruebas web sin fallos, incluidas las regresiones previas de geometría. Los casos nuevos cubren sumas/orden por autor, límites de progreso, LRU, vencimiento/revocación, reinicio con disco, rotación de firmas, cambio de cuenta, fallos de descarga, invalidación, concurrencia con precarga y timeout recuperable. En CUA se ejerció el StoryPlayer real con dos autores y medios sintéticos, avance 0→42→58 % para un libro de 12 segundos, nuevo autor en 0 %, reapertura/reinicio con archivos existentes sin nuevas peticiones de medios y cambio de hoja. Para capturar la perspectiva se usó además una compilación QA a 3000 ms; la compilación final vuelve a 520 ms. La web se comprobó con foto, video que termina y avanza, pausa/reapertura, una barra y perspectiva visible. No se escribieron publicaciones reales. No se afirma ejecución visual Android ni validación del libro en el teléfono físico.

Compilaciones firmada de iPhone y de simulador correctas: build/story-book-device-final.log y build/story-book-simulator-final.log. Tras confirmar el usuario que OGT estaba en el inicio, se instaló la versión final sin desinstalar ni borrar datos en iPhone 17R. La apertura automática fue rechazada por bloqueo del teléfono. Registros: build/story-book-iphone-{install,launch}.log. Evidencia: qa/artifacts/story-book/validation.json, web.png, web-turn.png e ios-page-turn-slow-qa.png. No hubo despliegue adicional de backend/web ni commit/push.
