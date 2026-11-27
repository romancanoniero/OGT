# Fotos de mascotas · 27/11/2026

## Baseline

Una adopción creada en el iPhone guardó `file://…/Library/Caches/ogt-media/…jpg` en `post_media.url`, `social_posts.media_urls` y `animal_listings.photo_urls`. El archivo nunca pasó por la subida autenticada; el navegador descarta esas rutas locales. No era un fallo del proveedor de imágenes ni del orden del feed.

`persistAnimalOnServer` serializaba las referencias locales sin subirlas. Las pantallas de adopción y mascota perdida capturaban errores y navegaban como si hubiera finalizado la publicación. Editar una foto remota podía convertir su URL en `asset://https://…`.

## Diferencias intencionales

- El flujo común sube fotos, videos preparados y sus portadas mediante la misma subida autenticada que Publicar, antes de enviar la ficha. Conserva el orden, la copia editada y los archivos remotos existentes. La sincronización de fichas locales pendientes usa el mismo recorrido.
- Una falla conserva el formulario y su ficha local, muestra el error y permite reintentar sin crear otra ficha local. Solo se navega y anuncia el post después de la respuesta correcta del servidor.
- Las rutas de publicación y edición de mascotas rechazan `file:`, `content:`, `asset:` y portadas locales antes de escribir en la base. Se conservan HTTP/HTTPS, el namespace público de medios y los campos/validaciones anteriores, incluidas las listas vacías que admitía la API.
- La fuente opcional, los permisos, los contactos de adopción, los avistajes, los mapas, las fechas, las restricciones de edición y los resultados comunitarios conservan su lógica.

## Verificación

Las pruebas nativas cubren subida previa al envío, fallo sin solicitud de publicación, reintento sobre la misma ficha, vinculación de IDs, conservación de fotos remotas y videos preparados/portadas. Las pruebas del servidor y los recorridos reales de navegador verifican rechazo de rutas de dispositivo, que un segundo usuario pueda decodificar la imagen y que no pueda editar la ficha ajena. Pasaron 162 pruebas de Android y 88 del servidor, y 18 recorridos reales en Chromium y WebKit móvil (adopción, mascotas perdidas, publicación, edición, mensajes, trueque, reconocimiento, convocatorias y estacionamiento). Android genera el APK. Los resultados de iOS y la eventual reparación limitada de la foto de Monchis se registran en los artefactos privados de QA.

La foto recuperada se mantiene privada hasta recibir autorización explícita para subirla a Contabo y vincularla al post existente. No se suben versiones a TestFlight sin una nueva petición.
