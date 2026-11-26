# Convocatoria: propósito, causa y corrección

## Propósito documentado

`shared/domain/CommunityRules.kt` establece que una convocatoria tiene día y lugar. `shared/domain/Feed.kt` la describe como una actividad comunitaria a la que se puede responder «Asistiré» y avisar a contactos; el botón está disponible antes de su inicio. La web ya distinguía tipos de actividad: huerta, merienda, reforestación, limpieza de playa, compost y trueque.

Dar a conocer reconoce una buena acción que hizo otra persona. Convocatoria invita a una actividad futura y se publica a nombre de quien la organiza. No exige señalar a una persona reconocida.

## Causa

El dock conservaba correctamente `ComposePostKind.GATHERING`, pero todas esas rutas acababan en `PublishActionScreen`. Su plantilla caía en las categorías y el título de Dar a conocer y omitía `publicationKind` en la petición genérica. Una categoría compartida como Reforestación podía activar la validación de reconocimiento de otra persona. Además, la edición decidía el formulario solo por el hashtag; el horario existía en el modelo local, pero no viajaba por el contrato de publicaciones del servidor.

## Corrección compatible

- Formulario propio `PublishGatheringScreen`, tanto desde el dock como al editar; `PublishActionScreen` redirige explícitamente GATHERING para cubrir los llamadores existentes.
- Actividad, relato, día, hora local, punto de encuentro y material visual. El envío identifica `CONVOCATORIA` y no incluye protagonista ni homenajeado.
- Migración aditiva 40 para lugar y comienzo, transporte por REST, actualizaciones y caché. La clasificación usa primero el tipo explícito y conserva el criterio anterior por etiqueta cuando el post histórico no tiene tipo.
- Los campos nuevos son opcionales en el contrato anterior. Editar el relato de una convocatoria antigua sin horario conserva ese estado; los horarios nuevos deben ser futuros. Editar una actividad pasada sin cambiar su horario sigue permitido. Se mantienen autor, imágenes, fuentes y permisos de edición.
- Los reconocimientos de Reforestación no se convierten en convocatorias. Las categorías administrables del reconocimiento siguen siendo un catálogo distinto de los tipos de actividad.
- No se reescriben publicaciones existentes ni se cambia el mecanismo de asistencia actual.

## Validación

Pruebas compartidas de tipos, fechas, huso horario, vencimiento, actualización y caché; integración de publicación/edición, propiedad, conservación de imágenes, reintentos y compatibilidad histórica. El navegador prueba que Convocatoria no pide una persona reconocida y conserva día y lugar al recargar y editar el relato. Se ejecutó junto con el recorrido de invitaciones y las regresiones existentes, en Chromium y WebKit móvil. Android e iOS compilan.

El paquete se publica con las migraciones explícitas 39 y 40 y las guardas de release existentes. La 39 admite repetición sin restablecer categorías modificadas o desactivadas. El SDK externo solo se incorpora cuando CI o el proceso de release pasan su ruta expresamente; la importación habitual del IDE conserva sus exclusiones.
