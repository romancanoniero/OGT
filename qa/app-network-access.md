# Acceso normal de las apps conservando el CRM privado — preparado 26/11/2026

Estado: **preparado y probado localmente; no activado en Contabo**. El pedido inicial autorizaba cuentas de testers con vencimiento. La propuesta posterior es que las tiendas filtren la instalación y cualquier cuenta Firebase válida pueda usar la app; activar ese alcance requiere la decisión explícita del usuario.

## Alcance concreto

La opción `OGT_PUBLIC_APP_HOST=onlygoodthings.lat` habilita únicamente las rutas de la app en el dominio canónico:

- API de los módulos actuales bajo `/api/v1/`, con una lista explícita de sus espacios de nombres. Firebase, el doble factor del proveedor, consentimiento, estado de cuenta y permisos de cada operación continúan a cargo del backend existente.
- Lectura de `/media/`: conserva las comprobaciones de visibilidad y firmas del backend. No habilita escrituras anónimas de archivos.
- Conexión a `/db`: el socket exige su mensaje de autenticación Firebase antes de permitir escuchar datos. Abrir la conexión no autentica al cliente.

Conserva el router de la web y el CRM con su lista de redes, `/api/v1/crm`, rutas internas, `/health`, `/release.json`, acceso directo a puertos únicamente por loopback, credenciales y el router independiente de notificaciones de pago firmadas. El modo de pagos continúa restringido a deshabilitado/sandbox por `release_guard`; no habilita pagos reales. Tampoco publica la web completa.

Sin esa variable, o con valor vacío/`disabled.invalid`, la nueva entrada está deshabilitada. La regla exige simultáneamente el host canónico y el configurado, por lo que tampoco puede habilitarse falsificando el host deshabilitado. `release_guard` rechaza cualquier host ajeno o expresión inyectada. La opción no amplía `OGT_PRELAUNCH_CIDRS` ni cambia `OGT_LAUNCH_MODE=prelaunch`.

Las conexiones nuevas tienen límites por dirección de origen: API 20 solicitudes/segundo con ráfaga de 100; sockets 5 conexiones/segundo con ráfaga de 20. No se confía en encabezados de IP enviados por el cliente.

La entrada temporal `/store-review` con identidades y vencimiento se conserva, pero no hace falta utilizarla en el modo normal de la app. Las versiones ya entregadas a TestFlight 1.0 (15) y Google Play 1.0 (17) usan las rutas canónicas; no requieren reconstrucción para este cambio del servidor.

## Verificación antes de activar

`python3 qa/check-app-network-access.py --baseline-ref 9474f6d` ejecuta Traefik 3.6.6 y nginx 1.28 en una red Docker aislada, con puertos de prueba exclusivamente en loopback y sin credenciales ni datos reales. Pasa **144 comprobaciones** con la opción deshabilitada y habilitada: acceso a la API solo después de la autenticación simulada, rechazo de sesiones inválidas, límites de rutas públicas, CRM bloqueado incluso con encabezados falsificados y rutas normalizadas/codificadas, medios privados rechazados, necesidad de autenticar sockets, métodos de escritura no habilitados para medios y preservación de rutas legales, webhook y revisión temporal. Todas las etiquetas previas se comparan con la revisión base y permanecen idénticas.

`python3 -m unittest qa.test_release_guard -v` correcto. `:backend:test` correcto: **82 pruebas**, sin fallos; Gradle reutiliza resultados vigentes porque no cambia el código del backend. No se modifican fuentes ni ejecutables de Android/iOS. La prueba de proxies utiliza una simulación de autenticación y no certifica por sí misma una sesión real de Firebase/MFA.

Tras autorizar la activación: respaldar privadamente la configuración y fijar únicamente la opción de acceso normal. Publicar la revisión mediante el procedimiento integral protegido del proyecto, conservando sus verificaciones, respaldo y manifiesto; no copiar un Compose por fuera del release verificado. El procedimiento existente recrea web/API y conserva sus volúmenes, credenciales y datos. Comprobar desde una red externa el rechazo de tokens inválidos con respuesta de la API, el bloqueo del CRM, la salud desde la red autorizada y las restricciones del socket. El ingreso real desde 5G debe comprobarse en el iPhone del usuario; nunca se extraen sus tokens.
