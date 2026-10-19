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
cd qa
npx playwright install chromium webkit
npm test
OGT_FULL_E2E=1 npm test
```

`npm test` sin `OGT_FULL_E2E=1` ejecuta la interfaz anónima y omite explícitamente
el circuito completo. No equivale a una certificación E2E. Los casos completos
utilizan Firebase emulado, API real y base de datos real; todavía requieren ejecución.
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
| Registro → API → persistencia → avatar → nueva sesión | Pendiente; Docker bloqueado por espacio/IO |
| Android: compilación completa | Bloqueada por ausencia de `google-services.json` |
| Android: compilación Kotlin omitiendo generación Firebase | Aprobada; no equivale a APK/configuración verificados |
| Producción: consulta de configuración sin mutaciones | Laboratorio habilitado; ruta Firebase Admin ausente |
| Clases backend de línea base frente a VPS | 174/175 idénticas; `SocialSqlRepository` diferente |

Los informes se generan en `backend/build/reports/tests`, `shared/build/reports/tests`
y `qa/playwright-report`. Capturas y trazas están en `qa/test-results`. La evidencia
de la VPS se conserva localmente bajo `.recovery`; no se publica ni se suben binarios.

Medición con imagen sintética texturada y EXIF: 6.135.720 → 1.905.840 bytes (68,9 % de reducción).
Un WebP transparente de 44 bytes se conservó en 44 bytes. No es una estimación de ahorro de producción.

## Diferencias intencionales de esta rama

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
  La validación de la configuración modificada dentro de Nginx sigue pendiente de Docker.

## Bloqueos y próximos controles

1. Recuperar Docker/espacio local y ejecutar E2E completo con PostGIS y Redis reales.
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
