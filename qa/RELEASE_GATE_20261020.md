# OGT — Gate de lanzamiento (20 de octubre de 2026)

Este procedimiento corresponde EXCLUSIVAMENTE a `romancanoniero/OGT`. La prioridad es la versión web; Android/iOS se auditan por separado. La referencia integrada inicial de `main` es `e925eedf7abee0416121f393cea73ba94d97f416`. Si `main` avanzó, examinar commits nuevos y no rebobinar.

## Reglas de seguridad

- No desplegar, editar ni reiniciar servicios de Contabo hasta superar QA y contar con autorización explícita para ese despliegue.
- No revelar ni copiar a logs, GitHub o al chat contraseñas, tokens, claves SSH, datos de clientes ni JSON de service accounts.
- No sobrescribir ni descartar cambios locales; nunca `reset --hard`, `clean -fdx` o `push --force`.
- Los tests crean únicamente cuentas y datos sintéticos en `demo-ogt-qa` y PostGIS de QA. Bloquear todo tráfico externo en pruebas E2E, según los tests existentes.
- Si falta una dependencia, registrar BLOQUEADO y su causa; nunca registrar como PASSED una ejecución que no ocurrió.
- No alterar `romancanoniero/retailnav`, OGT de otros repositorios, Llamarín u otros proyectos.

## 1. Verificar y aislar el trabajo local

En Cursor, dentro del repositorio abierto:

~~~sh
git remote -v
git status --short
git fetch origin
git rev-parse origin/main
git merge-base --is-ancestor e925eedf7abee0416121f393cea73ba94d97f416 origin/main
~~~

Comprobar que `origin` es `https://github.com/romancanoniero/OGT.git` o su equivalente SSH. Si hay cambios locales, preservarlos. Para QA, trabajar en un worktree separado basado en `origin/main`, con un nombre de directorio libre; no modificar el worktree habitual. Comprobar si `db-kmp-sdk` está disponible localmente; configurar la propiedad Gradle `ogt.dbKmpSdkPath` según corresponda. Informar las versiones de Java, Node y Docker.

## 2. Reproducir pruebas unitarias y estáticas

Desde la raíz del worktree QA:

~~~sh
node --check web/app/app.js
node --check web/app/auth.js
node --test qa/auth-unit.test.mjs
./gradlew :backend:test :shared:jvmTest :backend:installDist
sh qa/check-nginx.sh
~~~

Usar Java 17 o 21. Si falta el SDK compuesto, solucionarlo desde la copia local legítima, sin inventar dependencias. Registrar cantidad y resultado de cada prueba, no solo el exit code de la última.

## 3. Ejecutar E2E completos en el entorno aislado

Leer primero `qa/README.md`. Requiere Docker, Node 22, Firebase Auth Emulator, PostGIS y Redis de QA. Las pruebas deben usar únicamente el proyecto `demo-ogt-qa`, no `goodthings-55612`.

- Arrancar `docker compose -f qa/compose.yml up -d --wait`.
- En `qa/`, ejecutar `npm ci` y `npx playwright install chromium webkit`.
- Arrancar `npm run auth-emulator` en un proceso persistente (desde `qa/`).
- Arrancar el backend preparado, con estas variables de QA:

~~~sh
OGT_ENV=test OGT_ALLOW_DEV_TOKENS=false \
FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19099 \
OGT_FIREBASE_PROJECT_ID=demo-ogt-qa \
OGT_JDBC_URL=jdbc:postgresql://127.0.0.1:19432/onlygoodthings \
OGT_DB_USER=ogt OGT_DB_PASSWORD=ogt_qa_local_only \
OGT_REDIS_URL=redis://127.0.0.1:19379 \
OGT_MEDIA_DIR=qa/artifacts/media OGT_PUBLIC_BASE=http://localhost:19082 \
PORT=19083 backend/build/install/backend/bin/backend
~~~

- En otra terminal, desde `qa/`, ejecutar `OGT_FULL_E2E=1 npm test`. El config Playwright arranca la web local automáticamente.
- Conservar `qa/test-results`, `qa/playwright-report` y reportes de Gradle. No subir artefactos con información sensible.
- Documentar fallas reproducibles y arreglarlas en una rama de corrección; volver a ejecutar las pruebas tras cada corrección.

## 4. Controles de seguridad adicionales que deben pasar

- JWT inválido, vencido, manipulado y `dev.*` en producción: rechazo (401), sin sesiones creadas.
- Cuentas A/B: no deben poder alterar perfiles, publicaciones, mascotas, mensajes, dinero/puntos o recursos ajenos.
- WebSocket `/db`: auditar autorizaciones por ruta escuchada; impedir que un cliente autenticado se suscriba a rutas privadas ajenas, indiscriminadas o arbitrarias. Crear pruebas con cuentas A/B y denegación expresa.
- Sanear atributos y texto HTML generado con entrada de usuario; probar comillas, caracteres especiales y URLs peligrosas.
- Subidas: formato real, tamaño y megapíxeles; corruptos; animaciones; cargas paralelas; URLs y entrega pública.
- Denuncias, moderación, eliminación, solicitudes de datos, privacidad, permisos y rate limiting.
- Revisar que la migración `18_chat_mensaje.sql` se aplique una sola vez y que la inicialización limpia no dependa de datos editoriales de producción.
- Revisar que las pruebas de redes sociales / perfiles / búsquedas / Trueque / mascotas / estacionamiento no degraden funcionalidades históricas.

## 5. Verificación de producción SOLO de lectura

Tras QA local satisfactoria, verificar por SSH que la VPS de Contabo es la de OGT, comparar hashes y configuración instalada con `origin/main`, comprobar `OGT_ENV=production`, rechazo de tokens de laboratorio y que exista credencial Firebase Admin **sin imprimir valores**. Consultar `/health`, HTTPS, certificado, proxy de `/media/`, backups y espacio de disco. No asumir que la VPS equivale a GitHub: los parches publicados en octubre fueron selectivos. No aplicar scripts de migración sobre producción sin revisar si ya se aplicaron.

## 6. Criterio de OK y entrega

Emitir un informe breve con: SHA de la rama probada; pruebas unitarias/HTTP/E2E por navegador y total; tabla de PASS/FAIL/BLOCKED; hallazgos críticos y altos; diferencias con Contabo; plan y verificación de reversión; aspectos no verificados con Google/SMS reales, Safari/iPhone y usuarios; cambios y commits propuestos. Guardar informe en `qa/artifacts/` (ignorado por Git).

Solo declarar `GO` si no quedan fallas críticas/altas, el paquete exacto a desplegar pasó todas las pruebas, las condiciones de seguridad están cerradas, existe respaldo probado, el despliegue y su regresión se verificaron y se aprobaron los textos legales de uso y privacidad.

No desplegar antes de autorización expresa del usuario. Si todos los controles locales pasan, emitir `READY FOR STAGING`; si quedan riesgos, `NO GO` con razones y próximos pasos.
