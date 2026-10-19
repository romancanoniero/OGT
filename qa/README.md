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
