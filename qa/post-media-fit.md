# Multimedia completa dentro del marco

El feed, el perfil y el detalle mantenían un marco 16:9 que recortaba fotos y miniaturas para llenarlo. El detalle móvil usaba el ícono de ampliar para iniciar el video.

El cambio solicitado conserva el marco y centra el archivo completo, respetando su proporción, sobre `ink` de Quiet Studio (`#111213`). Las fotos y miniaturas publicadas usan `ContentScale.Fit` en la app; fotos y videos del carrusel web usan `object-fit: contain`. El reproductor iOS declara explícitamente `AVLayerVideoGravityResizeAspect`. El botón móvil usa el nuevo vector tintable `qs_play` en el feed y el detalle, con la etiqueta “Reproducir video”.

La presentación conserva los archivos, el orden, los puntos del carrusel y los valores de encuadre guardados. Avatares, selectores, edición de encuadre y otros usos del componente conservan su comportamiento anterior; la opción `wholeMedia` se activa por defecto únicamente para multimedia publicada. La reproducción y el regreso al perfil no cambian.

La validación aislada incluye las 168 pruebas de la app, APK Android y framework de iOS. La prueba visual web comprueba foto decodificada, fondo de la paleta, marco 620 × 349, video reproducible sin errores, conservación de la pieza seleccionada al actualizar y pausa al cambiar de pieza. La revisión de publicación debe verificar el código instalado y actualizar la app del iPhone antes de anunciar la entrega.
