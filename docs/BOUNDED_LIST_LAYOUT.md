# OGT — Listas contenidas y desplazamiento interno

Regla obligatoria desde el 25/10/2026. Complementa `DATA_VIEWS_STANDARD.md` para todas las pantallas de OGT Control y OGT Empresas que utilizan el espacio de trabajo compartido. No cambia el feed público ni las pantallas nativas.

## Problema y solución compartida

La tarjeta tenía límites horizontales, pero el espacio de trabajo solo definía una altura mínima. Las filas aumentaban la altura intrínseca de sus padres y desplazaban el documento completo. Poner `overflow-y: auto` únicamente en la tabla no corrige esa cadena.

`web/app/crm/crm-layout.css` establece la altura disponible del área autenticada y permite que cada nivel flex/grid se reduzca mediante `min-height: 0`, `min-width: 0` y una fila `minmax(0, 1fr)`. El encabezado de sesión conserva su altura real: no se resta una constante que falle cuando se envuelve el texto. El login mantiene el desplazamiento normal del documento.

La tarjeta redondeada recorta su contenido. Solo el contenedor de filas tiene desplazamiento vertical y horizontal; la cabecera de columnas queda fija dentro de ese contenedor. Búsqueda, filtros, orden, cantidad, selección y carga de más registros quedan fuera del scroll de filas y dentro de los límites del espacio de trabajo. No se ocultan registros ni se sustituyen las reglas de paginación.

## Alcance

- Tablas de Usuarios y Publicaciones; tablas de indicadores y secciones de Resumen, Empresas, Denuncias, Campañas, Cupones e Impacto del componente compartido.
- Ficha completa de usuario: encabezado, foto, indicadores y pestañas permanecen visibles; Resumen, Publicaciones, Denuncias y el contenido restringido de Chats ocupan un panel interno. No se modifica el permiso de acceso a chats.
- Revisión de usuarios seleccionados: lista interna con bordes redondeados y scroll, incluso con todos los registros cargados seleccionados.
- Comentarios, anécdotas, denuncias de publicaciones y resultados del selector de autor: colecciones internas delimitadas. Las acciones sensibles mantienen confirmación, permisos y auditoría existentes.
- Sugerencias y paneles de filtros/orden: se limitan al espacio real disponible, sin quedar cortados fuera de la pantalla.

Ambos puntos de entrada cargan la misma hoja y el mismo módulo `crm-layout.js`. El módulo añade regiones enfocables y etiquetas para teclado/lectores de pantalla, ajusta el espacio disponible cuando cambia la ventana o el teclado virtual y limita los paneles flotantes. No consulta datos privados, no autentica y no altera registros. No intercepta rueda, gestos ni teclas: el navegador desplaza el contenedor enfocado.

## Reglas de aceptación

La cantidad de registros nunca debe aumentar el alto/ancho del documento autenticado. La tarjeta permanece dentro de la ventana; el contenido se desplaza por dentro, con sus bordes redondeados. La carga incremental conserva los nodos de navegación, herramientas y filas existentes, además de la posición del listado cuando es aplicable. Los estados vacío/error y los textos largos no deben provocar desbordes.

`qa/crm-layout-browser.py` usa el componente real `mountWorkspace`, CSS/JS del repositorio y datos locales de prueba: nueve vistas en siete tamaños (63 combinaciones), además de paginación 20/40/60, selección, perfiles con muchas publicaciones/denuncias, comentarios, búsqueda de autor, filtros, teclado, scroll horizontal, errores, vacío y redimensionamiento sin recargar. Las capturas de escritorio/móvil y las de fallos se guardan junto con los informes del ejecutor QA. Las suites previas de autenticación e ingreso también siguen ejecutándose.

Estas pruebas de navegador no certifican operaciones con datos productivos ni todos los motores de navegador. El despliegue solo se declara instalado después de que GitHub Actions complete `publish`, verifique el SHA en Contabo y confirme la salud del backend, conforme a `DEPLOYMENT_POLICY.md`.
