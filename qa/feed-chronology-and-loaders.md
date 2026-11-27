# Orden del feed y actualizaciones silenciosas · 26/11/2026

## Baseline comprobado

El servidor conservaba 43 publicaciones: 20 visibles en el feed, 17 retiradas y 6 historias (una retirada). «Movilidad Eco» seguía visible, orgánica, con autor activo, sin eventos de ocultamiento y con fecha original `2026-11-26T01:01:41.908233Z`. No se restauran publicaciones moderadas ni se modifican fechas o contenido para corregir el orden.

La afinidad podía adelantar homenajes antiguos en Para vos. Además, el teléfono mantenía el orden recibido y podía anteponer una publicación propia reciente mediante su incorporación optimista. El usuario confirmó que la fecha de posteo debe determinar el orden visible, conservando los intereses para seleccionar recomendaciones.

## Diferencias intencionales

- El servidor pagina después de ordenar por `createdAtEpochMs` descendente. La personalización, la ampliación del inventario por hashtags, el ocultamiento, los bloqueos, las familias y las razones de recomendación permanecen. Para fechas idénticas se conserva la selección previa por afinidad/diversidad. Una recomendación por oficio o interés ya no adelanta una publicación antigua.
- La caché nativa y la web aplican el mismo criterio. Lo propio sigue incorporándose de inmediato, dentro de su fecha. Editar, recibir un contador o revalidar no cambia la fecha de posteo. Los posts ajenos nuevos siguen esperando en «Hay contenido nuevo» para conservar la posición de lectura.
- Al entrar, regresar o revalidar el feed no aparece un loader de actualización. Tirar manualmente para actualizar mantiene el contenido y muestra el indicador durante la solicitud; durante el gesto previo solo hay una indicación estática.
- Las historias automáticas y las imágenes del feed no muestran spinners. Un reintento manual de historias sí indica la solicitud, incluso si coincide con una actualización ya en curso. Se conservan la caché, la validación de privacidad, los errores y el selector de medios.
- Los indicadores de acciones iniciadas expresamente, como publicar, importar archivos o confirmar autenticación, conservan su funcionamiento. No se modifican los sockets ni se agrega sondeo.

La instalación y las comprobaciones finales se registran en los artefactos locales de QA. Las iteraciones se prueban en el iPhone 17R; no se suben nuevas versiones a TestFlight sin una petición del usuario.

## Aclaraciones sobre fuentes y permisos

Por pedido del usuario, el formulario conserva la fuente opcional y el enlace breve al acuerdo, y deja de repetir los párrafos extensos sobre permisos. Las secciones 6.1 y 6.7 de los Términos ya contienen esas declaraciones y obligaciones: no cambian el contrato, su versión, la aceptación al registrarse, la validación de enlaces ni los créditos junto al contenido publicado. El cambio se aplica al componente nativo compartido y a los formularios web.

## Comprobaciones

- 183 pruebas compartidas, 86 de backend, 158 de Android y 30 de integración con base de datos correctas. Las pruebas de base se ejecutaron en una base nueva de laboratorio: Docker se había cerrado, y la primera base reutilizada contenía reintentos financieros de pruebas anteriores. Se conservaron las bases y los volúmenes originales; no se omitió ningún caso.
- 42 recorridos completos correctos en Chromium y WebKit móvil, más 10 repeticiones enfocadas después de compactar los avisos del formulario. Incluyen publicación, edición, medios, reconocimiento, convocatorias, hashtags, lugares, invitaciones, registro, mascotas, ayuda, mensajes y estacionamiento.
- 97 comprobaciones de Node y 133 controles de código de publicación correctos. Android compila y el comparativo de lint conserva los 134 diagnósticos previos (73 errores, 58 advertencias y 3 notas), sin hallazgos nuevos ni supresiones.
- iOS genera un ejecutable ARM64 firmado para el mismo bundle y equipo, con Firebase, manifiesto de privacidad y recursos verificados. Se instala directamente en el iPhone 17R, sin desinstalar ni borrar datos. El usuario confirmó que la adopción nueva aparece primero. El Pixel sigue pendiente y no se sube otra versión a TestFlight.
