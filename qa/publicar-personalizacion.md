# Publicar, hashtags y preferencias — 26/11/2026

## Referencia y alcance

Base de comparación: `c9997dc`. Trabajo directamente en `main`. Los cambios están en fuentes y artefactos locales de prueba; no se despliegan ni se suben nuevos binarios a las tiendas durante esta implementación.

El menú común Android/iOS y la web ofrecen **Publicar** primero, unificando las entradas de Buena acción y Ternura. Se conservan los tipos y enlaces históricos; una entrada antigua de Ternura abre el editor unificado con ese hashtag seleccionado. Convocatoria, mascotas perdidas, adopción, intercambio de ayuda y homenajes mantienen sus formularios específicos.

## Publicaciones y catálogo

- Entre 1 y 8 hashtags por publicación. Las claves normalizan mayúsculas, acentos y espacios; los libres admiten letras, números y guion bajo, entre 2 y 40 caracteres. Se conserva la escritura visible de los hashtags libres.
- El catálogo existente del CRM incorpora una clave estable y la opción «Requiere reconocer a otra persona». Renombrar un hashtag mantiene su identidad; desactivarlo impide nuevos usos y permite conservarlo al editar publicaciones anteriores. Los nombres históricos con puntuación continúan siendo válidos.
- Los hashtags oficiales y libres se pueden combinar. Una etiqueta libre que coincide con una oficial se resuelve como la oficial, con sus reglas; la validación se realiza en el servidor.
- `#BuenaAcción` y las categorías históricas de reconocimiento requieren otra persona. Nombre completo, @nick, autoría e invitaciones siguen separados. Los créditos existentes se conservan al editar; las acciones históricas mantienen su compatibilidad.
- Se mantienen fotos/video, atribución y aviso de derechos, otras personas participantes, lugar opcional e invitaciones por las aplicaciones elegidas por el usuario. El lugar opcional de las nuevas publicaciones también se guarda en el servidor. Las fechas y RSVP siguen perteneciendo a Convocatoria.
- Publicar conserva un identificador de envío por borrador coherente, reutiliza los archivos ya subidos al reintentar y mantiene la operación de publicación/invitaciones en una transacción. Los errores conservan el borrador; cambiar de cuenta invalida la respuesta pendiente.
- Las menciones a participantes se completan desde el trabajo asociado a la cuenta, fuera del ciclo de vida del editor, con el token y transporte originales. Salir de la pantalla no las cancela ni permite que usen otra identidad.

La migración aditiva `41_publication_hashtags.sql` agrega metadatos e índices, completa los hashtags de contenido histórico sin cambiar cuerpo, medios, créditos ni tipos, y está incluida en el paquete y activación normales del release. No amplía el acceso al CRM ni modifica Firebase, MFA, restricciones de cuenta o modo de pagos.

## Selección de contenido

**Para vos** usa una primera versión determinista de preferencias, calculada para la cuenta autenticada. Aprende por hashtags y autores a partir de aplausos, corazones, comentarios, compartir, visitas al perfil y permanencia medida. Una impresión sola no equivale a interés. Ocultar excluye la publicación del inventario y aporta una señal negativa; omitir reduce su afinidad. Las repeticiones de la misma acción sobre un post no multiplican la preferencia.

Se consideran hasta 2.000 eventos recientes de los últimos 90 días, con pérdida gradual de peso y una vida media de 30 días. Los hashtags oficiales conservan afinidad por ID al renombrarse y comparten señales por clave con los libres. La selección combina afinidad temática, autor y frescura. Mantiene las restricciones de visibilidad, bloqueo y anuncios, así como los pedidos de conocidos que coinciden con lo que el usuario puede ofrecer.

El inventario incluye publicaciones recientes y publicaciones de hashtags afines, aunque estén fuera de las 200 más recientes. Se limita la repetición consecutiva de un autor y se reserva espacio para explorar otros temas. Sin señales suficientes se utiliza la fecha. No se entrenó un modelo externo ni se infieren temas de imágenes: esta implementación usa los hashtags y las interacciones existentes.

**Siguiendo** consulta el grafo de la persona y ordena exclusivamente por fecha; no aplica preferencias, promociones por habilidad ni pines personales que alteren ese orden.

El servidor decide la selección. Los contadores y las reacciones no reordenan las tarjetas visibles. La actualización explícita puede adoptar el nuevo orden; contenido nuevo queda pendiente de mostrar. La caché conserva el orden por consulta y sus recomendaciones más antiguas, se restaura tras reiniciar y se separa por cuenta. Se mantienen sockets, comprobación de visibilidad y reconciliación existentes; no se agrega polling.

## Verificación

Antes del cambio, las suites compartidas, backend y Android pasaron con sus resultados vigentes. Después se ejecutan las suites completas y los recorridos nuevos en la base aislada `ogt_business_qa_publicar20261126`, Firebase emulado y servicios en loopback, sin datos ni mensajes reales.

Resultados finales:

- **440 pruebas sin fallos**: 182 compartidas, 82 de backend, 29 de integración con PostGIS y 147 de Android. Incluyen dos cuentas con intereses opuestos, hashtags libres dentro de la misma familia, renombrado, duplicación de eventos, exploración, ocultación, recuperación fuera de los 200 posts recientes, orden cronológico de Siguiendo, identidad y caché después del reinicio.
- **28 recorridos completos** correctos en Chromium y WebKit móvil: sesiones, publicación/edición/eliminación, propiedad del contenido, medios, mensajes, animales, ayuda, estacionamiento, CRM e invitaciones con registro y retorno al mismo post. La última ejecución focalizada repite sus **6 casos de publicaciones e invitaciones** en ambos navegadores e incorpora el reenvío HTTP exacto sin duplicar el post.
- La prueba HTTP directa verificó publicación normal, escritura visible del hashtag libre, lugar opcional y su borrado explícito, edición del propietario, reintento idempotente, eliminación y rechazo de usar tipos antiguos con hashtags múltiples para omitir las reglas. Detectó y corrigió la comparación de IDs transitorios de los medios en los nuevos `POST`; los fingerprints de contratos anteriores permanecen sin cambios.
- **97 pruebas Node y 133 controles del proceso de release** correctos. Sintaxis Python, JavaScript, shell y espacios del diff correctos.
- CRM: suites de navegador, entrada y auditoría de interacción correctas; la auditoría registra cero errores y un aviso de tamaño de acción existente. Una ejecución de desplazamiento agotó la espera del teclado; se conservaron ese resultado y su traza. La referencia anterior y la ejecución diagnóstica actual pasaron las mismas 18 comprobaciones de desplazamiento sin cambiar CSS, JavaScript ni la prueba para ocultar el fallo.
- Android genera `composeApp/build/outputs/apk/debug/composeApp-debug.apk`, con firma v2 verificada. `lintDebug` mantiene los **73 errores previos**: una comparación exacta entre la referencia y el cambio obtiene 139 ubicaciones de diagnóstico idénticas, con cero hallazgos nuevos. No se agregaron supresiones ni una baseline de lint.
- iOS genera la aplicación completa para simulador ARM64/x86_64 en `build/ios-publicar-simulator-validation/Build/Products/Debug-iphonesimulator/iosApp.app` y el archivo Release ARM64 para iPhone en `build/ios-publicar-validation/OGT-final.xcarchive`. Ambas firmas pasan verificación profunda/estricta; el archivo de iPhone conserva el UUID de símbolos correspondiente. Se verifican Firebase de producción, manifiesto de privacidad y los 143 recursos de Compose. El usuario autorizó localmente la solicitud del llavero; no se extrajeron claves ni contraseñas.
- Reaplicar la migración anterior 39 y la nueva 41, incluida una segunda ejecución de 41, conserva exactamente la huella de cuerpo, medios, tema, tipo, créditos, categoría y lugar/fecha de los posts de laboratorio.
- Revisión visual de la web móvil con WebKit: sin desbordamiento horizontal, foto de prueba decodificada y hashtags oficiales/libres visibles. Captura en `qa/artifacts/publicar-mobile-form.png`.

La evidencia sin datos personales queda en `qa/artifacts/publicar-personalizacion-20261126.json`, excluida de Git. No se instalaron aplicaciones ni se cambiaron datos en los emuladores del usuario. La generación y firma no equivalen a una prueba funcional en un iPhone físico.

## Orden de publicación

Para habilitar esta versión fuera del laboratorio, publicar primero backend/CRM con la migración 41 mediante el procedimiento integral existente y después distribuir nuevas compilaciones móviles con los números de build requeridos por las tiendas. Los clientes anteriores conservan sus contratos. El editor nuevo necesita el backend actualizado para el contrato `POST` con múltiples hashtags. Esta tarea no modifica las versiones instaladas ni publica los binarios generados.
