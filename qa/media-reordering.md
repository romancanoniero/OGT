# Orden de fotos y videos · 27/11/2026

## Baseline y alcance

Los selectores múltiples nativos compartían `OgtDraftMediaStrip`: un toque elegía la portada y mantener pulsado solo permitía llevar una pieza al héroe. La tira no tenía posiciones de destino. En la web, los tres formularios múltiples usaban `bindPublishMedia`, que dibujaba miniaturas sin reordenamiento. La edición de mascotas mostraba fotos remotas aparte y elegir otras reemplazaba todas las anteriores.

El cambio cubre los selectores múltiples de Publicar, reconocimiento/homenajes, convocatoria, intercambio de ayuda y mascotas (adopción y pérdida) que ya ofrecían carga de archivos. Los selectores de una única imagen, como perfil e historias efímeras, conservan su funcionamiento. No se habilita una edición de medios nueva en pantallas que antes solo permitían editar el texto.

## Diferencias intencionales

- Android e iOS permiten mantener pulsada una miniatura, arrastrarla entre las imágenes y soltarla en la posición indicada. Los bordes desplazan la tira únicamente durante el gesto, con velocidad independiente de la frecuencia de pantalla. Cancelar no altera la lista. Se conserva el toque y el arrastre al héroe para elegir portada.
- El detector vive en el contenedor para conservar el gesto cuando una miniatura deja la zona visible. Se mantienen el desplazamiento normal antes de armar el arrastre, los IDs estables, las copias de video preparadas, la elección de audio, las portadas, la eliminación y los límites existentes. Los controles de accesibilidad permiten mover una posición sin arrastrar.
- La web ofrece arrastre con mouse y pulsación prolongada con tacto, marcas de inserción y desplazamiento durante el gesto. Se cancela al salir, ocultar la página o pulsar Escape. También ofrece botones y teclado. No hay sondeo ni animación permanente cuando está inactiva.
- La edición web de mascotas integra las fotos ya subidas en el mismo selector. Las nuevas se añaden al conjunto y las anteriores se quitan explícitamente si se desea reemplazarlas, conservando ambas posibilidades. Mover fotos remotas no vuelve a subirlas. La validación y el límite de diez se aplican a la selección completa.
- La selección web se separa por usuario y formulario/listing. Navegar a otra edición no mezcla fotos; regresar conserva el orden y las incorporaciones sin guardar, incluso una selección deliberadamente vacía. Un guardado que termina después de navegar solo limpia la selección de su publicación. Esto conserva selecciones de medios en memoria, sin añadir persistencia de todos los campos del formulario.
- Se toma una copia de la selección al subir y se transmite su orden como `sortOrder`. El primer elemento sigue siendo la portada. No se modifican las fechas, reacciones, permisos de edición, categorías, invitaciones ni el contenido de publicaciones existentes.

## Comprobaciones

166 pruebas de Android correctas, incluidas selección por ID, límites y posiciones visibles, cambios de selección y conservación de video sin sonido/portada al subir en el orden nuevo. Android genera un APK firmado e iOS genera un ejecutable ARM64 firmado para el mismo bundle/equipo, con Firebase y el manifiesto de privacidad verificados.

116 comprobaciones de Node y 30 recorridos de regresión de navegador correctos (28 de regresión completa y dos de guardado demorado). Los recorridos del nuevo selector verifican mouse, eventos táctiles de WebKit, cancelación, botones, colores reales de las imágenes descargadas, persistencia del orden, edición con fotos remotas y nuevas, una única subida nueva y conservación de fecha y texto. También reproducen el cruce de selección antes de corregirlo y verifican que dos ediciones y sus guardados no mezclen ni limpien las fotos del otro aviso. La comprobación visual valida que las miniaturas estén realmente decodificadas; el laboratorio permite `blob:`/`data:` locales manteniendo bloqueados todos los destinos externos.

Android lint conserva los 134 diagnósticos anteriores (73 errores), sin hallazgos nuevos ni supresiones. Los controles de accesibilidad web y los resultados del despliegue/instalación se registran en los artefactos privados de QA. El Pixel sigue pendiente y no se sube a TestFlight sin una nueva petición.
