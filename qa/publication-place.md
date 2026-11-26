# Lugares de publicación y TestFlight 1.0 (16)

Referencia del selector de lugares: `6580c96`, que ya incluye Publicar, hashtags y preferencias. Se trabaja directamente en `main`. El pedido posterior del usuario autoriza distribuir el resultado en TestFlight; necesita desplegar primero el backend compatible con Publicar y el catálogo del CRM.

## Diferencias intencionales

- En Publicar, incluir un lugar sigue siendo opcional. Se elimina el barrio del perfil como supuesto lugar actual. Al activar la opción, se intenta obtener una posición real, con el aviso de uso y los permisos existentes de Android/iOS. Un permiso bloqueado no abre Ajustes automáticamente. Una posición de más de dos minutos no se presenta como actual.
- El lugar admite escritura libre o sugerencias de direcciones, plazas y localidades al escribir al menos tres caracteres, después de 450 ms. Elegir una sugerencia completa su nombre. Las opciones aparecen en un desplegable anclado al campo, con selección por toque y teclado, sin quitarle el foco al texto. El usuario puede cambiarlo o quitarlo antes de publicar. Una respuesta tardía del GPS o del buscador no reemplaza decisiones posteriores.
- Convocatoria conserva su formulario, fecha, hora, organizador, validaciones y RSVP; su campo de encuentro incorpora el mismo buscador y la acción de ubicación actual. En la web, enfocar un campo vacío también ofrece resolverlo con GPS; en la app se usa su acción explícita.
- Sin GPS, permiso, señal o proveedor disponible se conserva la entrada manual. La búsqueda manual no exige GPS. Abrir Publicar sin incluir lugar no solicita ubicación. Editar un post con lugar guardado no lo sustituye automáticamente por la ubicación actual.
- Solo se persiste en el post el nombre confirmado; no se agregan coordenadas GPS al contrato de publicación ni a la base de publicaciones. La web incorpora la visualización del lugar guardado de los nuevos `POST`; el encabezado y detalle nativos ya lo mostraban.
- Nuevas rutas autenticadas `/api/v1/geo/places/search` y `/api/v1/geo/places/reverse`, consumidas por Android/iOS a través del repositorio compartido y por la web. Reutilizan el modelo `GeoHit`. No modifican las rutas anteriores de estacionamiento ni de mascotas.
- El proveedor de publicación es Photon, que admite búsqueda incremental. `OGT_PHOTON_URL` permite cambiar desde el servidor a una instancia propia. Sin configuración utiliza el servicio público de Photon, con una sola consulta por 1,1 s para toda esta instancia, caché de diez minutos limitada a mil entradas y tiempos de espera acotados. El servicio público admite uso moderado y no garantiza disponibilidad; para crecer se requiere instancia propia o proveedor contratado. No se agrega autocompletado sobre Nominatim público, cuya política lo prohíbe.
- Se elimina el texto auxiliar «Podés escribir cualquier lugar». Los créditos de OpenStreetMap/ODbL y Photon/Apache 2.0 quedan en Ajustes → Créditos y licencias, manteniendo las atribuciones que ya pertenecen a los mapas. Se conserva acceso a los créditos editoriales.
- No se introduce polling: las búsquedas responden a edición del usuario; la app observa una posición válida durante una solicitud puntual, con espera máxima de diez segundos.
- iOS incrementa únicamente su número de compilación de 15 a 16. Android conserva su versión y distribución existentes.

## Contabo y CommonLib

Consulta de solo lectura: no aparecen contenedores, servicios systemd ni procesos de OSRM, Valhalla, Nominatim, Photon o Pelias; tampoco están configuradas sus URL para OGT. Las rutas a pie existentes conservan sus alternativas externas. Photon responde correctamente desde Contabo a una consulta de un monumento público.

El VPS tiene seis procesadores lógicos, aproximadamente 12 GB de RAM, 2,2 GB disponibles y 35 GB de disco libres. El usuario solicitó cobertura mundial. Según la documentación de Photon, la base mundial ronda 95 GB, se recomiendan 64 GB de RAM para operaciones fluidas y se requiere espacio para dos bases durante una actualización. Esta tarea no instala datos mundiales ni amplía el VPS. Para Nominatim mundial la documentación recomienda al menos 1 TB de disco y 128 GB de RAM para importar el planeta.

Se inspeccionaron los fuentes publicados de **CommonLib 0.1.2** que OGT consume en Android e iOS: ofrece modelos portables, incluyendo `GeoSearchResult`, `UserAddressModel` y `PlaceModel`, pero no un cliente de geocodificación o autocompletado. No se cambia la versión ni el código de esa dependencia.

Fuentes: [Photon](https://github.com/komoot/photon), [política de Nominatim público](https://operations.osmfoundation.org/policies/nominatim/), [requisitos de Nominatim](https://nominatim.org/release-docs/latest/admin/Installation/), [OSRM](https://project-osrm.org/).

## Verificación local

- Pruebas compartidas: 182 correctas. Backend: 86 correctas, incluidas consultas reales HTTP a un proveedor simulado, autenticación de las rutas, validación de coordenadas, orden longitud/latitud, caché concurrente y recuperación después de errores.
- Android: 147 pruebas correctas y APK generado con el selector final. Las pruebas de permisos y de las funcionalidades vecinas se conservan.
- **38 recorridos completos** correctos en Chromium y WebKit móvil: incluyen GPS opcional, cambio manual, autocompletado, carreras entre respuestas, rechazo de permiso, proveedor caído, quitar el lugar, persistencia y visualización tras recargar, formulario específico de Convocatoria, sesiones, medios, feed personalizado, Siguiendo, invitaciones, registro, animales, ayuda, mensajes y estacionamiento.
- La primera ejecución dejó 36 casos correctos y dos fallos que detectaron la ausencia de visualización del lugar en la web. Se corrigió la pantalla y se repitieron los 38 casos sin cambiar la exigencia de persistencia de la prueba.
- Consulta HTTP por el backend autenticado de laboratorio al Photon real: cinco sugerencias para Montevideo y resolución de coordenadas de Plaza de Mayo. Solo cuenta Firebase emulada y coordenadas públicas sintéticas, sin identidades nuevas en producción.
- Las pruebas y los artefactos del cambio anterior permanecen documentados en `qa/publicar-personalizacion.md`. El lint de Android ya tenía 73 errores previos, con comparación exacta sin hallazgos nuevos en esa etapa; no se silencian ni se elimina ninguna comprobación.

Las firmas, el resultado final de integración de base de datos, los hashes de los ejecutables, la revisión desplegada y el estado de Apple se registran al finalizar en `qa/artifacts/publication-place-testflight-16.json`, excluido de Git. No se modifica ni instala nada en los emuladores del usuario. La compilación no sustituye la prueba del recorrido con GPS en el iPhone físico.

## Ajustes posteriores solicitados por el usuario

La captura del iPhone correspondía al formulario nuevo ejecutándose mientras Contabo aún tenía `1ff9684`: la ruta nueva devolvía 404. La misma consulta «Buenos aires» a Photon desde Contabo devolvió cinco resultados. El despliegue integral `e977445`, ejecución [45889126038](https://github.com/romancanoniero/OGT/actions/runs/45889126038), terminó correctamente y activó la ruta autenticada; un token inválido pasa a recibir 401. No se extrajeron tokens ni datos de sesión del iPhone.

El usuario pidió un desplegable, quitar el texto auxiliar y llevar los créditos a Ajustes. La app usa el desplegable editable de Material 3 de su versión actual, sin actualizar dependencias. La web ofrece un combobox/listbox anclado, navegación con flechas/Enter, cierre con Escape y selección táctil. La primera prueba táctil detectó que cancelar `pointerdown` impedía la selección en Safari móvil; se conserva el comportamiento de desplazamiento táctil y se previene únicamente el cambio de foco del evento de ratón. La prueba verifica el valor elegido antes de publicar. Los créditos no requieren cambiar preferencias de cuenta ni modificar la cuenta.

La comparación de los 43 posts con el respaldo previo detectó que el trigger genérico de `updated_at` había marcado el backfill de hashtags como una edición. Solo ese campo había cambiado; no se alteraron cuerpo, archivos ni otros campos anteriores. Se corrigió la migración 41: suspende únicamente el trigger de fecha dentro de una operación atómica bloqueada y restituye exactamente su modo; no modifica los otros triggers. Se agrega una prueba de reaplicación, igualdad de todos los campos excepto hashtags y actualización de fecha tras una edición real. Las 30 pruebas de integración pasan.

Las fechas de los 43 posts se recuperaron en el servidor desde el respaldo anterior. Se comparó cada fila completa antes de escribir, se guardó metadata reversible en el respaldo privado y la operación abortaba si una fila había cambiado. El trigger quedó activo. La huella de todos los campos previos vuelve a ser exactamente `d010863b157e69d698f20690aa18e67f`, con los mismos 16 usuarios y 43 posts. La configuración protegida de Firebase, acceso y CRM mantiene la huella anterior.

Una ejecución local de navegador agotó el tiempo al crear una página y al abrir el registro mientras compilaba iOS. Se conservaron los registros de la ejecución y se detuvo solo ese proceso de laboratorio para repetir la verificación sin cambiar límites ni cerrar aplicaciones del usuario. No se modifican las pruebas de autenticación para ocultar esa espera. La evidencia final distingue esos resultados del recorrido final.
