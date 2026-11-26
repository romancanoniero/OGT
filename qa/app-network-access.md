# Acceso normal de las apps conservando el CRM privado — activado 26/11/2026

Estado: **activado en Contabo y verificado desde una red externa**. El usuario aprobó explícitamente «Sí, activar acceso normal de la app», reemplazando la limitación temporal a cuentas de testers. Las tiendas administran quién puede instalar sus versiones de prueba; el servidor acepta cuentas Firebase válidas desde cualquier red y conserva los controles de cuenta y permisos. Esto no convierte la instalación desde una tienda en una credencial de autenticación.

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

## Activación y regresión en Contabo

Revisión instalada: `1ff9684adfce978c6235bf49686023a651f4c3b2`. Publicación integral [45868319107](https://github.com/romancanoniero/OGT/actions/runs/45868319107), con checks, backend e integraciones, navegador, base de datos y publicación correctos. La comprobación de 144 rutas también se ejecutó correctamente en CI. El checkout del trabajo de checks conserva el historial para comparar sus etiquetas con la revisión base; no se omitió ninguna verificación.

Se respaldó privadamente `.env` en el servidor y se agregó únicamente `OGT_PUBLIC_APP_HOST=onlygoodthings.lat`; la comparación posterior confirma que todas las otras opciones siguen idénticas. La instalación se realizó mediante el procedimiento integral protegido, con sus verificaciones, respaldo y manifiesto. No se copiaron archivos de despliegue por fuera del release verificado.

Verificación real posterior, sin credenciales ni datos personales de usuarios:

- Manifiesto del servidor con la revisión exacta; API saludable, web activa, cero reinicios y puertos directos solo en loopback.
- Desde una red fuera de la lista autorizada: **15 comprobaciones HTTPS correctas**. La sesión con un token deliberadamente inválido responde `401` JSON `INVALID_TOKEN`, en lugar del bloqueo de red anterior. Feed, ajustes y consentimiento sin credenciales responden `401`, como exige el autenticador existente de Ktor; ese rechazo puede no tener cuerpo JSON.
- CRM de control y empresas, API del CRM, raíz web, salud y manifiesto continúan en `403` desde esa red. También se rechazan intentos con encabezados de IP falsificados y rutas del CRM codificadas o normalizadas. La política de privacidad sigue disponible; un medio inexistente devuelve `404`.
- Socket TLS `/db` verificado tanto desde la red externa como desde la red autorizada: acepta la conexión, rechaza escuchar datos sin autenticar y rechaza un token inválido con `UNAUTHENTICATED`; luego cierra con el código existente `1003` (`CANNOT_ACCEPT`). No se recibieron datos de usuarios ni se generaron identidades de prueba en producción.
- Salud por HTTPS correcta desde la red autorizada. Firebase de producción conservado, tokens de desarrollo deshabilitados, emulador de autenticación deshabilitado, CRM con las mismas cuatro redes permitidas y pagos en sandbox.
- Base antes/después: **16 usuarios y 43 publicaciones**, con el mismo resumen de identificadores de publicaciones. No se alteraron datos para probar el acceso.

La diferencia intencional es el acceso a las rutas nativas de la app desde otras redes; la web completa y el CRM conservan sus restricciones. La evidencia sin datos personales queda en `qa/artifacts/app-network-access-20261126.json`, excluida de Git. Se solicitó al usuario reintentar el ingreso desde 5G: esa aceptación con una cuenta real continúa pendiente y no se sustituye por las pruebas negativas de autenticación. Nunca se extrajeron sus tokens ni códigos de doble factor.
