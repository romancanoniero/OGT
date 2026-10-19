# OGT: verificación de lanzamiento

Base de producción recuperada: `recovery/vps-baseline-20261019` (`3a726d6`).
Trabajo de lanzamiento: `launch/readiness-20261019`. No desplegar esta rama todavía.

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
| Clases backend de línea base frente a VPS | 174/175 idénticas; `SocialSqlRepository` diferente |

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
- El script de despliegue verifica la configuración segura antes de copiar archivos.
- Se recupera la prioridad `^~ /media/` ya presente en GitHub: las fotos y videos
  subidos deben llegar a la API en lugar de resolverse como archivos estáticos de Nginx.
  `sh qa/check-nginx.sh` valida la configuración y el enrutamiento de JPG, WebP,
  MP4, API y health en un contenedor aislado con upstream simulado. No prueba TLS
  ni la configuración externa de la VPS.

## Bloqueos y próximos controles

1. Ampliar la cobertura E2E: el circuito de autenticación/perfil y los rechazos de API
   ya pasan con PostGIS y Redis reales; no equivalen a certificar todos los módulos.
2. Reconciliar el código exacto de `SocialSqlRepository` con producción. La VPS no
   contiene `loadMatchedFriendNeeds`; no sustituir el backend hasta preservar y probar
   tanto el comportamiento desplegado como los cambios existentes en GitHub.
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
