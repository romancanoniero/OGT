# Recuperación de sesión nativa · 26/11/2026

## Fallo comprobado antes del cambio

En el iPhone 17R, OGT 1.0 (16) permitía ingresar, pero al cerrar y volver a abrir mostraba el formulario de autenticación. Las preferencias de OGT confirmaron que no se había cerrado sesión (`ogt.signedOut=false`), que el onboarding estaba completo y que el ingreso biométrico estaba activado. La copia temporal de preferencias se eliminó después de conservar únicamente esos indicadores; no se extrajeron credenciales del llavero.

La restauración ya distinguía una identidad guardada con biometría mediante `AuthRestore.NeedsBiometric`. El error estaba en la navegación: ese resultado llevaba a `Login`, cuya biometría solo se inicia al tocar el botón. La reapertura anterior al cambio no solicitó `/auth/session`, coherente con detenerse antes de desbloquear la identidad guardada. Las 29 pruebas existentes de autenticación eran correctas, pero no cubrían ese destino de navegación.

## Cambio intencional confirmado con el usuario

El usuario confirmó que una sesión válida debe abrir directamente el feed y que el bloqueo biométrico debe ser opcional y estar desactivado inicialmente. Se separó la preferencia explícita de bloqueo (`app_lock` en Android, `ogt.appLock` en iOS) del antiguo acceso biométrico del login (`bio` / `ogt.bio`). Usar ese botón de ingreso no activa el bloqueo. No se borra ni se convierte automáticamente la preferencia antigua: aunque esté activa, el bloqueo nuevo permanece apagado hasta elegirlo expresamente en Ajustes.

- Por defecto, la restauración valida la identidad guardada con Firebase y OGT y navega al feed sin formularios ni solicitudes biométricas. Los pasos pendientes de consentimiento, perfil u onboarding siguen siendo obligatorios.
- Ajustes → Seguridad en este dispositivo → Bloquear OGT al abrir permite elegir el bloqueo. Activarlo requiere una sesión válida y una verificación biométrica exitosa; cancelar, no disponer de biometría o cambiar de cuenta durante la verificación no guarda la activación. Desactivarlo desde la sesión abierta no exige otra verificación.
- Solo con ese bloqueo activado, `NeedsBiometric` abre una pantalla específica para desbloquear la sesión guardada. Inicia la verificación biométrica una sola vez cuando la app está activa; luego usa el mismo intercambio Firebase → OGT y la navegación existente hacia el feed, consentimiento, perfil, permisos o invitación pendiente.
- Cancelar o fallar la biometría mantiene la pantalla de desbloqueo, con reintento y acceso alternativo. No se elimina la identidad, no se inicia un bucle de solicitudes biométricas y no se permite acceder al feed sin verificarla.
- La pantalla forma parte del bloqueo de autenticación de los enlaces entrantes. Ninguna invitación permite adelantarse al desbloqueo.
- El cierre explícito, las credenciales revocadas, el segundo factor, la privacidad del CRM y los flujos de registro conservan su política anterior. La diferencia intencional es que el viejo acceso biométrico ya no obliga a desbloquear la app en cada arranque.

## Validación

Se agregaron tres regresiones de navegación y siete de la política de activación/desactivación del bloqueo. También se agregó una comprobación instrumentada de Android para la independencia de la preferencia antigua y la nueva; se compila, pero no se ejecuta en el Pixel pendiente. La validación de compilación Android e iOS y la repetición física del cierre/reapertura se registran en el artefacto local `qa/artifacts/native-session-reopen-20261126.json` al completarse.

Por indicación del usuario, esta corrección se prueba con una instalación local en el iPhone 17R, conservando el bundle, equipo y datos existentes. No se sube otra versión a TestFlight. La compilación 16 ya distribuida permanece disponible y no incorpora esta corrección posterior.

Resultado físico: la instalación conservó la identidad de Firebase. Los procesos 12535 y 12537 corresponden a dos aperturas distintas, con cierre real de OGT entre ambas. En la segunda se confirmó visualmente el feed y respuestas correctas de sesión y publicaciones, sin volver a ingresar ni exigir biometría. Las iteraciones posteriores conservan esa política; el usuario confirmó además que la adopción recién publicada aparece primero.
