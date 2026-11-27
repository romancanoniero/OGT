# Adopciones: rediseño compatible (27/11/2026)

## Diferencias intencionales

- El alta y la edición muestran «Título del posteo» y «Presentación», para uno o varios animales. El título conserva las mayúsculas elegidas. Carácter se integra en la presentación; no se elimina el dato histórico.
- Edad e historia/cuidados son opcionales. Se mantiene «Qué hogar necesita».
- Especie, tamaño, sexo y salud se eligen como etiquetas administradas en Control. Se permiten varios tamaños/sexos/especies. Vacunación y castración distinguen todos, algunos, ninguno y sin informar. Una casilla histórica desmarcada no se convierte en una afirmación negativa.
- Se pueden añadir seis hashtags libres. Coincidencias con claves o nombres del catálogo conservan la identidad administrada y su desactivación. #Gatos/#Perros mantienen la identidad de intereses del catálogo existente.
- Barrio o localidad usa el mismo buscador incremental, dropdown accesible y GPS de las publicaciones. Se permite confirmar un barrio manual si falla el proveedor; una respuesta tardía no reemplaza lo escrito.
- La modalidad juntos/por separado y la cantidad disponible son opcionales. Reducir la cantidad mantiene el aviso abierto. El autor cierra explícitamente toda la adopción desde el detalle con confirmación, conservando la política de verificación y puntos.
- Fuentes y créditos queda disponible en el alta/edición. Sin una URL guardada no se genera enlace, rótulo ni bloque vacío en feed, detalle o perfil. Los créditos editoriales reales y las licencias del software en Ajustes se conservan.

## Compatibilidad y conservación

La migración 43 agrega una columna JSONB nullable y un catálogo versionado; no rellena ni modifica publicaciones antiguas. El replay se probó con una ficha histórica, medios encuadrados, fuente, comentario y reacción: sus huellas permanecieron iguales.

La nueva presentación vive separada del texto histórico de la ficha. Al actualizar una adopción se mantienen nombre/temperamento anteriores como datos de compatibilidad, fecha de publicación, identificadores y fechas de las fotos, originales, fuentes, encuadre, orden, duración y reacciones/comentarios. Los clientes anteriores pueden seguir publicando y editando sus campos sin borrar los detalles nuevos ni el título completo.

El feed entrega los detalles animales en una consulta por página, también en publicaciones cerradas o accesibles por enlace directo fuera del primer lote. Los cambios de detalles se hidratan a partir del evento existente, sin polling ni loaders automáticos. Cerrar una adopción conserva tanto los contadores como las marcas de reacción del usuario. Recibir dos veces los mismos detalles no genera un cambio social adicional.

El catálogo se administra con la misma sesión privada, MFA reciente y roles OWNER/OPERATIONS del catálogo existente. Tiene control de versión, desactivación y auditoría; los anuncios anteriores conservan las etiquetas seleccionadas.

## Verificación reproducible

- Dominio compartido: 188 pruebas; backend: 91; integración con PostGIS: 31; cliente Android: 169. Sin fallos ni omisiones en la ejecución final de estas pruebas. Incluye serialización, guardado/reintento nativo, edición desde un cliente anterior, propiedad y conservación exacta de los medios.
- Node: 122 pruebas; controles de publicación: 133 pruebas Python; automatización: 5; editorial: 6; sintaxis Python: 128 archivos sin errores.
- Regresión completa del candidato: **66/66** comprobaciones Chromium y WebKit móvil, incluidas 14 del rediseño, sin omisiones. API/PostGIS/Redis y SDK web recompilado reales de laboratorio, Firebase emulado.
- `adoption-redesign.spec.mjs`: alta grupal, edición histórica desde el menú, créditos ausentes/agregados/borrados, catálogo real del CRM, restricciones de MFA/roles, barrio manual/GPS tardío y cierre parcial/total y presentación en animales cercanos. Ejecuta Chromium y WebKit móvil con API, PostgreSQL, Redis y Firebase emulado locales.
- La fixture de MFA es exclusiva del emulador de laboratorio; no simula una inscripción real a segundo factor ni utiliza credenciales productivas.
- `media-order`, `social-e2e`, `gathering`, `publication-hashtags`, `publication-place`, `post-invitations`, `user-blocks`, `settings-consistency` y `profile-photo` cubren los recorridos adyacentes. No se omiten aserciones de conservación; solo se actualizan los nombres de los campos pedidos.
- Controles de publicación del navegador: cuatro suites aprobadas; auditoría de Control sin errores (se conserva un aviso sobre tamaño de un control existente). Rutas de red: 144 comprobaciones, sin cambios en las restricciones originales.
- Búsqueda real de Caballito y Buenos Aires mediante el proveedor configurado: cinco resultados por consulta, sin mock. La interacción con el dropdown se prueba por separado de la disponibilidad del servicio.
- Android: APK generado y firma v2 verificada. iOS: ejecutable arm64 1.0 (18), firma estricta, Firebase, permisos de notificaciones, dominios asociados y manifiesto de privacidad verificados. No se publica en TestFlight ni Google Play.

Durante las pruebas se corrigieron dos pérdidas previas al regrabar medios: la fecha del registro en el servidor y el encuadre después de hidratar la respuesta en el cliente nativo. También se verificó que limpiar una fuente borre el enlace en la web aunque el JSON omita el valor null, y que Cancelar cierre efectivamente cierre el diálogo.

Los informes de ejecución y capturas se guardan localmente en `qa/artifacts/` y `qa/test-results/`, excluidos de Git. El iPhone 17R figuró desconectado durante esta verificación; la instalación física y su aceptación se registran aparte del resultado de WebKit móvil.
