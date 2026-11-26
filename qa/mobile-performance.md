# Rendimiento móvil: historias y ubicación

Se conserva la interfaz, el orden de historias no vistas, publicación/borradores, caché acotada, reproducción, sesión, permisos, frecuencia del GPS, sincronización de ubicación y detección de estacionamiento de Android e iOS.

## Diferencias intencionales

- Se elimina el estado observable que cambiaba cada 250 ms con el visor cerrado. El riel cambia con datos, vistos, publicaciones, eliminaciones, vencimientos o vencimiento de autorización. Se programa únicamente el próximo límite, sin un reloj que invalide continuamente Compose.
- Las historias escuchan los eventos `child_changed` de revisiones que emite el gateway y verifican al iniciar el observador. El respaldo cubre también reconexiones sin avisos. Se vuelve a asociar la suscripción al cambiar la generación del transporte autenticado. Los avisos recibidos durante una consulta se agrupan en una consulta posterior; no se descartan por estar ocupada la anterior.
- El respaldo deja de consultar cada 10 s. Cada respuesta válida programa una renovación a 20 s; una respuesta provocada por el socket reinicia ese plazo. Los errores reintentan a 5, 10, 20 y hasta 40 s. Se conserva el límite de **30 s desde la última verificación del servidor**: si no se renueva, las historias se ocultan y no se puede resolver su medio desde caché. No se confunde `OgtSdk.isStarted()` con salud de la conexión: el SDK fijado no expone esta última.
- Se usa tiempo transcurrido y un límite adicional por reloj de pared, para que atrasar el reloj no extienda la autorización y dormir el dispositivo no conserve historias vencidas.
- El indicador de carga del riel corresponde a la carga inicial y sus reintentos; la renovación de fondo exitosa no hace parpadear el indicador.
- El riel utiliza elementos diferidos con claves por autor y agrupación/orden memorizados. Conserva tamaños, espacios, nombres, avatares y acciones.
- La coordinación de GPS se mueve a una composición hija. Un estado estable expone campos observables independientes: una nueva muestra invalida a los lectores del fix, sin invalidar la navegación por ese motivo. Se conservan todos los efectos de permisos, servicios, muestras de movimiento, presencia del auto y sincronización remota. Las acciones delegan a los callbacks nativos más recientes.
- El reloj de avance de 40 ms continúa **solo dentro del visor activo**, cuando la foto está lista y se reproduce; se preservan pausa, video y navegación.

## Comprobaciones

`StoryLeaseClockTest` comprueba el vencimiento exacto de autorización, el primer vencimiento de historia, ausencia de despertares sin historias, renovación, cambio de cuenta, cambios de reloj y límites de reintento. `LocationUiStateTest` observa los estados de Compose y comprueba que una nueva muestra no invalida a quien solo lee permisos/servicios, mientras sí actualiza a quien lee el fix, y que las solicitudes usan los callbacks actuales.

Se ejecutan las 117 pruebas portables de Android y las 165 compartidas, incluida la lógica de movimiento y estacionamiento; también se compilan Android e iOS Simulator. El recorrido visible y las muestras de Inspector se conservan en el artefacto local `qa/artifacts/recomposition-analysis.json`.

Los contadores de recomposición y de hijos son distintos. No equivalen a redibujos, FPS ni consumo de CPU. La muestra anterior tomada con depuración e Inspector activos no es un benchmark de producción. No se afirma un porcentaje de mejora sin una medición comparable.

Referencia primaria: https://developer.android.com/develop/ui/compose/performance/bestpractices (lectura de estado cerca de sus consumidores, claves estables y cálculos memorizados).
