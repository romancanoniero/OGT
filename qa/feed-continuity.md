# Feed continuo al navegar — 26/11/2026

## Baseline y causa

Ya existen caché persistente de hasta 80 posts (`SocialFeedSnapshot` en preferencias), una base en memoria y caché multimedia en memoria/disco (`OgtMediaCache`). El efecto visual no requería agregar otra base de datos: `FeedScreen` creaba `feedPosts=[]` e `initialSyncComplete=false` en cada montaje. Esperaba visibilidad y REST antes de pintar, también al regresar de otra sección. El listener de `observeSocialPosts` pertenecía a esa pantalla y se cancelaba al salir. Los filtros, orden remoto y posición tampoco tenían un propietario que sobreviviera a esa navegación.

La decisión histórica de validar una caché recién importada protege contra tarjetas retiradas y autores provisionales durante el arranque (QA README, “Tarjeta provisional durante el arranque”). Se conserva esa protección en la primera apertura de la sesión; la navegación posterior no vuelve a bloquear el contenido ya disponible.

## Comportamiento nuevo

- `AuthController.feedTimeline` conserva una proyección por modo y familia durante la sesión. `FeedPresentation` retiene filtros, posición por consulta y la publicación propia ya presentada. Cambiar de cuenta o cerrar sesión cancela trabajos y descarta esas proyecciones.
- Las pantallas leen la caché local. Revalidar no la vacía, ni reinicia el indicador de carga inicial. Una consulta reciente se reutiliza 30 s; una más antigua se actualiza en segundo plano conservando sus tarjetas. No hay sondeo periódico del feed.
- Los contadores y cambios conocidos actualizan las mismas tarjetas mediante la base compartida y claves estables. Se mantienen las escrituras optimistas existentes y su reversión al fallar. Una respuesta iniciada antes de una reacción, edición, eliminación o cambio de medio no sobrescribe ese cambio local.
- Los posts nuevos se hidratan por REST autenticado antes de incorporarlos, con sus medios completos. Entran en la caché aunque el feed no esté abierto; se muestran mediante el aviso existente “Hay contenido nuevo”. No desplazan las tarjetas que se están leyendo. Tocar el aviso incorpora los posts inmediatamente y vuelve arriba. El aviso permanece hasta consumir las novedades, en lugar de desaparecer a los 4,2 s.
- La publicación propia sigue apareciendo inmediatamente, con un único desplazamiento al volver a verla. Los cambios ordinarios no producen el salto al inicio que antes podía ejecutarse con cada `feedTick`.
- Tirar para actualizar espera la solicitud real, incorpora el orden actualizado y conserva la lista durante la espera; se retiran las demoras artificiales de 380/80 ms.
- El observador pertenece a la sesión, no al feed. Mientras la app está activa también recibe cambios en otras secciones. Se pausa al dejar la app en segundo plano y se vuelve a asociar al regresar o cambiar el transporte autenticado. No modifica los sockets de estacionamiento, avisos ni perfiles.
- El gateway envía un `value {}` al suscribirse a `SOCIAL_FEED`; se usa como señal de conexión/reconexión para reconciliar, además de los `child_changed` que llevan cambios. El SDK fijado requiere listeners de ambos tipos. Se agrupan las solicitudes simultáneas y una reconexión durante una solicitud programa una única comprobación posterior.
- Si no hay red, se conserva el contenido local. Una retirada confirmada se guarda inmediatamente, antes de pedir la página siguiente. No se elimina por mera ausencia en una página HOME. FOLLOWING se reconcilia con la selección autorizada del servidor. Los resultados tardíos de otra cuenta o de una hidratación cancelada se descartan.
- El snapshot agrega `viewerId` opcional. Conserva el formato anterior y evita atribuir los flags de aplauso/corazón a otra cuenta. Un snapshot legado sin propietario se importa; esos flags se vuelven a obtener del servidor en lugar de adjudicarlos a una identidad desconocida. No se borran posts, textos, medios, contadores ni preferencias.

## Corrección adyacente

Se corrigió `observeStoryInvalidations` introducido en la optimización anterior: el gateway publica las revisiones de historias como `child_changed` y no como `value` de raíz. Se usa el listener de revisiones existente, con verificación al iniciar. Se conserva el vencimiento y respaldo de 20 s del controlador; no se vuelve a introducir el reloj observable de 250 ms. No requiere cambios en servidor o SDK externo.

## Validación

`FeedTimelineTest` usa eventos y respuestas controlados, sin credenciales personales ni escrituras en producción. Cubre navegación sin lista vacía, reutilización y error de red, validación y guardado de retiradas, uso sin conexión, socket sin pantalla suscripta, nuevas tarjetas con medios, rechazo de un post no autorizado, eliminación durante consulta e hidratación, reacciones/ediciones/eliminaciones optimistas, orden explícito frente al refresco pasivo, cambio de cuenta, filtros/estado de desplazamiento y regreso/reconexión sin duplicar observadores.

`FeedCacheOwnershipTest` cubre restauración de la misma cuenta, cambio de cuenta y formato legado. Se ejecuta junto con las suites existentes de Android, shared y backend; se compilan Android e iOS Simulator y se genera el APK.

La instalación y comprobación visual del Pixel continúan pendientes por indicación del usuario; no se reinicia ni se borra el emulador. Las pruebas de controlador y conservación del objeto de desplazamiento no sustituyen una observación de todos los frames en dispositivo. La primera apertura tras reiniciar la app puede seguir mostrando la carga inicial mientras se valida la caché, como la decisión de privacidad previa.

Referencias: https://developer.android.com/topic/architecture/data-layer/offline-first y https://developer.android.com/develop/ui/compose/state-hoisting.
