# OGT · Preparación de QA E2E web / CRM / iOS

Repositorio **único**: `romancanoniero/OGT`. No recuperar, desarrollar ni publicar desde `OnlyGoodThings`. Android está fuera de este ciclo.

## Situación y límites de las pruebas

- La última publicación integral *confirmada* antes de habilitar esta QA fue `c7ac7bf5f50c245b892b98c370ad627f899f445c`, en la instalación existente `https://onlygoodthings.lat`.
- `qa/crm-browser.py`, `qa/crm-entrypoints-browser.py` y `qa/crm-modules-qa.test.mjs` usan datos de prueba/mocks o verifican contratos de código. **No prueban autenticación real con Firebase ni permisos del backend**.
- `qa/live-guest-browser.py` revisa el navegador contra la web **real**, en modo visitante: ninguna escritura, ningún ingreso, ningún intento de MFA. Verifica que las tres URLs devuelven el recurso correcto y que la interfaz de acceso de cada CRM existe.
- El modo `OGT_LAUNCH_MODE=prelaunch` admite solo IPs autorizadas; GitHub Actions probablemente no lo esté. Un `403` en runners no justifica quitar la barrera: ejecutar desde la Mac/red explícitamente autorizada o habilitar un runner propio con IP fija debidamente autorizada.
- No hay validación E2E real de todos los módulos hasta contar con las identidades QA y el plan de datos de prueba del apartado siguiente. Un servidor con `health=OK` no prueba que un usuario pueda entrar.

## Matriz de aceptación: navegadores (desktop y móvil)

| Producto | Área observable | Qué verificar en E2E real | Estado inicial |
| --- | --- | --- | --- |
| App pública | Inicio, onboarding e ingreso | video, navegación, email/Google/teléfono, recuperación y MFA | pendiente, requiere identidad QA |
| App pública | Buscar, publicaciones, foto y video | búsqueda; crear, editar, publicar, encuadrar y borrar **solo posts QA** | pendiente |
| App pública | Mascotas, ayuda, mensajes y avisos | consultas, interacciones y notificaciones entre dos identidades QA | pendiente |
| App pública | Perfil, ajustes y billetera | foto de perfil, privacidad, navegación y datos propios | pendiente |
| App pública | Empresas, campañas y comunidad | navegación de módulos sin datos reales de terceros | pendiente |
| OGT Control | Resumen | ver métricas autorizadas tras login + TOTP | pendiente |
| OGT Control | Usuarios | leer detalle con rol correcto; verificar prohibición sin rol | pendiente |
| OGT Control | Empresas | leer organizaciones/pendientes; permisos de administración | pendiente |
| OGT Control | Denuncias | bandeja/moderación de contenido QA, trazabilidad de acciones | pendiente |
| OGT Control | Publicaciones | listado, detalle, comentarios y edición de encuadre en un post QA | pendiente |
| OGT Empresas | Resumen | identidad y empresa seleccionada correctas | pendiente |
| OGT Empresas | Campañas | lista y acciones disponibles para rol otorgado | pendiente |
| OGT Empresas | Cupones | visibilidad y operación exclusivamente en empresa QA | pendiente |
| OGT Empresas | Impacto | métricas, saldo, aislamiento entre tenants | pendiente |

**Precisión de producto:** los módulos Control y Empresas actuales contienen secciones de indicadores y una vista de publicaciones; la presencia del menú no demuestra que exista todo el CRUD de gestión solicitado. Cada acción inexistente debe marcarse `NO IMPLEMENTADA`, no aprobarse por tener pantalla.

### Conjunto mínimo de cuentas QA (no guardar claves en Git)

- Un usuario común (sin privilegios administrativos).
- Un administrador Control autorizado, correo verificado, TOTP configurado.
- Un empleado de **empresa QA A** con rol autorizado.
- Un empleado de **empresa QA B**, sin acceso a A.
- Dos cuentas comunes QA para mensajes/notificaciones.

Las cuentas, sus TOTP y cualquier secreto se administran fuera del repositorio, preferentemente en secrets de un entorno GitHub protegido o gestor de credenciales. No almacenar tokens ni capturas con datos personales en artefactos públicos. Datos usados para E2E deben ser **sintéticos y limpiables** (publicaciones, comentarios, empresas de QA); no borrar usuarios o datos reales en pruebas.

### Condiciones para declarar CRM web listo

1. `checks` y `browser` verdes para el SHA que está en Contabo.
2. Acceso real desde red autorizada en Safari iPhone, Safari Mac, Chrome escritorio y móvil.
3. Firebase login + segundo factor real; Control y Empresa autorizados, usuario común rechazado.
4. Aislamiento negativo: Empresa A no puede ver ID de Empresa B, incluso alterando URL/solicitud.
5. Todas las secciones de la tabla recorridas y con resultados (OK, fallo, bloqueada o no implementada) documentados.
6. Confirmar que el ambiente privado y sus datos QA no exponen información de producción.

## Ejecución actual

**Pruebas de código y browser simulado**: `Actions → OGT QA - Web y CRM → Run workflow` con `live=false`. No necesita cuentas reales.

**Smoke de invitado en sitio publicado**: misma acción con `live=true`, **solo** cuando la IP origen del runner esté permitida. Para correr desde la Mac autorizada:

```bash
cd /Volumes/DEV_MAC/OGT
python3 -m venv /tmp/ogt-qa-venv
source /tmp/ogt-qa-venv/bin/activate
python -m pip install 'playwright==1.56.0'
python -m playwright install chromium
python qa/live-guest-browser.py
```

Capturas sin sesión: `qa-artifacts/guest-smoke/`. No contiene interacción autenticada. Si se actualiza GitHub, sincronizar la Mac mediante `git pull --ff-only`, conservando los dos archivos locales sin trackear.

## iOS (antes de Android)

`Actions → OGT iOS - Simulator / TestFlight → Run workflow`.

- `distribution=simulator`: compila Kotlin/Native + proyecto Xcode en macOS con firma **deshabilitada**, publica `OGT-iOS-Simulator-<run>-<sha>` como artefacto. **No se instala en iPhone físico ni equivale a TestFlight.**
- `distribution=testflight`: requiere firma Apple Distribution para `com.onlygoodthings.app`, perfil App Store, y clave API de App Store Connect; compila, firma y entrega IPA. Una subida exitosa **no equivale a disponibilidad en TestFlight**: Apple debe procesar la versión y debe existir acceso de testers.

**Compilación actualizada para dispositivos iOS modernos (octubre de 2026):** el runner GitHub es `macos-26`, con Xcode 26 o posterior, porque App Store Connect ya no acepta nuevas entregas iOS compiladas con Xcode 16. El fallo del simulador del 25/10/2026 se debió al `GoogleService-Info.plist` ausente en CI. Antes de compilar, el workflow ahora consulta por HTTPS la configuración **oficial y actual** de la aplicación iOS registrada en Firebase `goodthings-55612`, verifica `com.onlygoodthings.app` y la incorpora únicamente en el runner; no utiliza configuración Android, credenciales falsas ni un plist construido a mano. Las credenciales Firebase Admin existentes se leen por canal SSH ya configurado en el entorno protegido. La cuenta de servicio necesita permiso de solo lectura `firebase.clients.get` para recuperar esa configuración; si Google lo rechaza, el workflow se detiene y no se produce una versión engañosa.

Además, se retiró de iOS el indicador de Firebase `isAppVerificationDisabledForTesting=true`, que es exclusivo de pruebas y no debe estar activo en builds distribuibles.

El workflow reutiliza el SDK KMP privado fijado por `OGT_SDK_REVISION`. El entorno protegido actual `ogt-launch` aporta el token SDK y revisores existentes; cualquier separación futura de entorno iOS debe mantener secret management y aprobación explícita.

### Secretos requeridos para distribuir por TestFlight

En los secrets del entorno protegido usado por el workflow, sin exponer nunca su contenido:

| Secret | Qué se debe cargar |
| --- | --- |
| `OGT_SDK_READ_TOKEN` | token GitHub con lectura de `romancanoniero/db-kmp-sdk` |
| `OGT_ASC_KEY_ID` | Key ID de una clave API de App Store Connect habilitada para subir builds |
| `OGT_ASC_ISSUER_ID` | Issuer ID de App Store Connect |
| `OGT_ASC_PRIVATE_KEY_B64` | contenido **base64** del archivo privado `AuthKey_*.p8` |
| `OGT_IOS_DISTRIBUTION_P12_B64` | certificado Apple Distribution con su clave privada, en `.p12` **codificado base64** |
| `OGT_IOS_DISTRIBUTION_P12_PASSWORD` | contraseña del archivo `.p12` |
| `OGT_IOS_APPSTORE_PROFILE_B64` | perfil de distribución App Store para el bundle ID del proyecto, en base64 |

El proyecto declara equipo Apple `WER3D825VB`, bundle `com.onlygoodthings.app`, iOS mínimo 16.0. Verificar que el equipo y perfil reales correspondan y que App Store Connect tenga registrada esa app; el workflow no puede crear certificados, perfiles, cuenta de testers ni acuerdos Apple automáticamente.

### Validación de disponibilidad de TestFlight

Una vez que el job termine con subida exitosa, revisar App Store Connect → TestFlight → Build correspondiente al `github.run_number`; confirmar **procesado** y que el grupo de probadores internos recibió acceso. Solo entonces se anuncia una fecha/enlace real de prueba. Registrar commit, número de compilación, fecha de procesamiento y dispositivo/versión iOS usada.

Android: **pospuesto expresamente** a la siguiente etapa. No se lanza su pipeline aquí.
