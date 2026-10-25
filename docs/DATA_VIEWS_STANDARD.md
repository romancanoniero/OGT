# OGT — Estándar obligatorio de pantallas de datos
**Versión 1.1 · 25/10/2026 · Ámbito: OGT Control y OGT Empresas**

Este documento define el patrón de cualquier pantalla que presente registros, tablas, listas de tarjetas, búsquedas, informes o resultados con acciones. Es un **contrato de producto, UX, acceso, frontend, backend y pruebas**. Toda pantalla nueva o modificada debe cumplirlo o registrar una excepción técnica justificada. No reemplaza `design/stitch/DESIGN.md` (identidad visual) ni `docs/CRM_ARCHITECTURE_20261021.md` (autorización/seguridad); los complementa.

## 1. Principios y estructura

1. **Primero la información.** Encabezado discreto, navegación persistente, tabla o tarjetas protagonistas. Evitar paneles de búsqueda y configuración altos.
2. **Barra compacta:** campo de búsqueda, botón **Filtros (n)**, botón **Orden (n)** en una única franja (objetivo 44–48 px de alto). En mobile se adapta sin desbordar.
3. **Configuración bajo demanda:** panel flotante anclado en escritorio; hoja inferior en teléfonos. No desplazar el listado para abrir configuraciones.
4. **Resumen compacto de criterios:** debajo, máximo una fila horizontal desplazable de chips con `×` individual y resumen de orden `1 Fecha ↓ · 2 Nombre ↑`. Mostrar conteos de criterios.
5. **Eliminar no es borrar registros.** La `×` solo quita un filtro; el icono de papelera queda exclusivamente para acciones administrativas de eliminación. «Limpiar filtros» y «Restablecer orden» son operaciones distintas.
6. Navegación y componentes de la pantalla mantienen su identidad durante consultas: **no recargar la página, desmontar la vista ni recrear toda la tabla**.

## 2. Búsqueda, filtros y ordenamiento

**Búsqueda**: es incremental, tolera espacios y mayúsculas/minúsculas, usa debounce aproximado 250–350 ms y cancela/descarta respuestas obsoletas. El alcance de los campos buscables se documenta por listado (no buscar datos privados si no hay autorización). Las sugerencias se muestran sin tapar los controles y se cierran con Esc, elección, pérdida de foco o clic afuera.

**Filtros**: son acumulativos. Valores múltiples **del mismo campo** se combinan con **OR**; campos diferentes con **AND**; la búsqueda libre también se agrega con **AND**. El estado vacío equivale a «todos». Permitir quitar chips individuales y limpiar solo filtros. Los campos enum se eligen entre valores permitidos, no mediante texto arbitrario; texto libre debe validar longitud y formato. Rangos de fechas inclusivos para el usuario, implementados como `>= inicio` y `< día siguiente` con zona documentada. No inferir una fecha del huso de un servidor accidentalmente. Los criterios se validan otra vez en backend.

**Orden**: varios niveles por prioridad ordenada (principal, secundario, terciario...), cada uno con dirección ascendente/descendente; se puede agregar, quitar, invertir y cambiar prioridad. Un campo no se repite. Encabezados muestran flecha y prioridad, con `aria-sort` en el principal. El backend añade un desempate determinista por ID estable aunque el operador no lo elija. Filtros y órdenes son independientes y se aplican sobre todo el conjunto autorizado, **no sobre los 20 registros visibles**.

## 3. Paginación y rendimiento

- Backend aplica en orden: **permisos y tenant → búsqueda → filtros → orden multi-nivel → cursor/límite**.
- Lotes por defecto: **20 registros**, con `nextCursor` opaco de tipo keyset, nunca «descargar todos y ordenar en navegador». Si se requiriera otro tamaño, justificarlo.
- El cursor conserva consulta, filtros, secuencia de orden, direcciones y valores de continuación, y se **rechaza** cuando no coincide con la solicitud o está mal formado. Los campos SQL surgen únicamente de listas blancas.
- «Cargar 20 más» agrega nuevas filas sin duplicarlas; cambiar cualquier criterio vuelve a la primera página. Las consultas en vuelo usan identificador de petición/abort para ignorar resultados viejos.
- Índices y planes SQL se revisan si crecen los datos; evitar consultas N+1, `OFFSET` profundo y conteos costosos por cada tecla. `nextCursor` permite indicar «hay más» sin un `COUNT(*)` obligatorio.

## 4. Actualización parcial y conservación de contexto

- **Solo actualizar contenido dependiente del resultado:** filas afectadas, encabezados de orden cuando cambian, contador, selección y estados de carga. Mantener el menú, búsqueda, chips, popovers y tablas montados.
- Reconciliar filas por **identificador estable**. Reutilizar nodos si su contenido no varió; si el orden cambia, mover filas sin vaciar el cuerpo de la tabla.
- Conservar foco del usuario, selección de texto en campos, posición horizontal y, cuando corresponda, scroll vertical. Al cargar siguientes páginas no saltar al inicio.
- Durante consulta conservar filas anteriores con estado sutil `aria-busy`; ante error conservar resultados válidos y mostrar error con posibilidad de reintentar. Nunca presentar una tabla vacía transitoria como resultado definitivo.
- **Estado por sección:** al navegar al detalle y volver, recuperar búsqueda, chips y órdenes durante la sesión. En un cambio real de cuenta/tenant, revocación o cierre de sesión, limpiar estado sensible.
- Si hay actualizaciones en tiempo real, reconciliar solo las filas cambiadas y respetar el conjunto filtrado. Para cambios que alteren el orden, refrescar consulta mediante API manteniendo el contexto.

## 5. Estados, acciones y detalles

- Diferenciar «cargando», «sin resultados para estos criterios», «sin registros», «sin permiso», «error de red» y «sesión caducada». Texto útil; no estados silenciosos.
- Las columnas describen datos reales; no completar con datos ficticios. Valores faltantes muestran «No informado» o «Sin actividad», nunca un cero inventado.
- Acciones de lectura abren diálogo/hoja de detalle accesible sin remount. Esc y cierre explícito; retorno de foco al disparador.
- Acciones sensibles requieren rol y MFA recientes, confirmación deliberada, motivo, auditoría y, cuando corresponde, doble aprobación. **No inventar acciones de mutación para una pantalla de solo lectura.**
- Selección múltiple se aplica a los registros del lote mostrado y debe estar etiquetada; si opera sobre todos los resultados, la interfaz debe explicarlo expresamente.
- Los resultados de búsqueda, exportación y detalles se limitan a campos necesarios y autorizados.

## 6. Seguridad, privacidad y permisos

- Verificación de identidad, rol, tenant y alcance en **cada endpoint**, jamás solo en UI. Los índices no pueden exponer datos de otras empresas.
- Aplicar mínimo privilegio, limitar longitud y cardinalidad de filtros, validar enum/fechas/cursor, consultas SQL parametrizadas, límites de respuesta y rate limiting de acceso sensible.
- El listado de usuarios **no incluye correos, teléfonos, Firebase UID, tokens, secretos ni historiales sensibles** por defecto. Identificador interno visible solo dentro del CRM autorizado. Implementar permisos adicionales antes de agregar PII, exportaciones o modificación de cuentas.
- Redactar o bloquear datos privados en métricas/logs, no interpolar texto en HTML. Fecha/hora y estatus reflejan datos efectivamente almacenados.
- Cada tabla debe documentar: rol permitido, tenant, campos devueltos, búsqueda, filtros, orden, límites y acciones posibles.

## 7. Diseño adaptable y accesibilidad

- Desktop: tabla compacta, cabecera y orden accesibles, scroll horizontal **dentro** del listado si el ancho no alcanza.
- Tablet: reducir espaciados, mantener interacción táctil. Móvil: toolbar adaptable y paneles tipo `bottom sheet`; no ocultar columnas sin alternativa para leerlas.
- Tamaños de toque 44 px cuando sea posible; etiquetas y nombres accesibles, teclado completo, orden de tabulación correcto, foco visible, Esc para cerrar, estados `aria-live`/ `role=status` y `aria-busy`. Contraste WCAG 2.2 AA.
- Usar tokens y componentes de Quiet Studio; evitar colores o iconografía que confundan «quitar filtro» con «eliminar usuario/publicación».
- Localización y fechas según `locale` del operador; definir explícitamente zona para rangos y marcas de tiempo, mantener la misma semántica entre frontend y backend.

## 8. Contrato de datos reutilizable (ejemplo)

```json
{
  "query": "ana",
  "filters": {"statuses": ["ACTIVE"], "roles": [], "from": "", "to": ""},
  "orders": [{"by": "createdAt", "direction": "desc"}, {"by": "name", "direction": "asc"}],
  "cursor": null
}
```

Respuesta: `{ "users": [<registros autorizados>], "nextCursor": null | "<opaco>" }`, dentro del envelope `ApiResponse`. Los campos disponibles y sus filtros dependen de cada pantalla. El cursor jamás sustituye la verificación de permisos.

## 9. Definición de terminado / QA

Antes de integrar: (1) prueba unitaria de parsers, whitelists, cursores y semántica de filtro/orden; (2) búsqueda incremental y respuestas fuera de orden; (3) QA real o con API simulada del DOM estable, sin recarga; (4) carga de 20 + 20 sin duplicados; (5) combinaciones AND/OR y eliminación individual de chip; (6) empty/error/loading, navegación de regreso y mobile; (7) rechazo de usuario/rol/tenant no autorizado; (8) revisión de PII y XSS; (9) regresión de las otras pantallas.

**Flujo obligatorio de publicación OGT:** verificar repositorio exacto `romancanoniero/OGT` → cambio en rama → pruebas y PR → integración en `main` → GitHub Actions → despliegue Contabo → verificar revisión instalada/health → entregar URL para prueba. **Checks verdes no significan deploy** si `publish` fue omitido.

## 10. Ficha de pantalla: OGT Control → Usuarios (v1)

- **Propósito:** explorar el padrón de usuarios sin alterar cuentas.
- **Permisos:** OWNER, OPERATIONS, SUPPORT y TRUST_SAFETY de OGT Control. Otras funciones y empresas no autorizadas: 403.
- **Datos del listado:** nombre visible, estado, rol, fecha de registro, última actividad e ID interno en ficha de lectura. No email/teléfono/Firebase UID.
- **Búsqueda:** nombre visible o UUID exacto. **Filtros:** estados múltiples (PENDING, ACTIVE, SUSPENDED, DELETED); roles múltiples (USER, COMPANY_ADMIN, COMMUNITY_MODERATOR); fecha de alta desde/hasta. **Órdenes:** fecha de alta, nombre, estado, rol, última actividad; hasta cuatro prioridades. Desempate por ID.
- **Lista y detalle:** 20 por página, carga incremental, panel compacto de filtros/orden, chips removibles, casillas de selección y ficha a pantalla completa con foto, identidad, alta, última actividad, publicaciones paginadas, puntos y denuncias de contenido restringidas. Las conversaciones privadas no se consultan libremente.
- **Actualización:** resultados y encabezado únicamente, con conservación del estado por sección.
- **Fuera de alcance v1.1:** edición, bloqueo/reactivación, exportación masiva, exploración general de chats, búsqueda por datos de contacto y eliminación física de cuentas. Exigen privilegios, motivo y un flujo seguro. La lectura del perfil ya queda auditada.

## 11. Mantenimiento del estándar

Al introducir un nuevo tipo de pantalla, proponer su **ficha específica** y revisar con producto cualquier interacción nueva antes de replicarla. Los patrones comunes deben ser parametrizables y probados una vez en el componente compartido; evitar bifurcaciones visuales sin razón. Conservar las excepciones documentadas con fecha y responsables.


## 12. Paridad entre publicaciones web, Android, iOS y CRM (25/10/2026)

**Regla:** todos los canales públicos y el CRM consultan las mismas publicaciones persistidas en `social_posts` con `moderation_status = 'VISIBLE'`. No se asume que los primeros 20 registros sean el total: el CRM muestra 20 por lote y continúa con keyset; la búsqueda recorre el conjunto entero en base de datos. Las fichas abiertas de animales `animal_listings` se asocian al post mediante `listing_id` y solo deben mostrarse al público si la publicación vinculada existe y es VISIBLE. La sección de Mascotas, la portada web y los clientes nativos no pueden resucitar un post moderado mediante un segundo origen.

**Ficha de adopción:** el nombre y título que muestra la app se obtienen de `animal_listings.pet_name/title`. El CRM muestra ese título junto con el cuerpo y su índice de búsqueda contiene ambos valores, con actualización al editar y backfill de posts históricos (migración 27). El índice solo incluye publicaciones visibles.

**Límite importante:** publicaciones locales sin sincronizar, borradores y datos de demostración empaquetados dentro de la app no cuentan como publicaciones públicas hasta estar confirmados por el backend. Una publicación pública real debe poder identificarse por el mismo UUID de `social_posts` desde cualquier cliente y encontrarse en el CRM mediante su nombre, título o ID.

**Aceptación:** buscar una adopción publicada hace semanas (por ejemplo `Luna`) debe encontrarla sin cargar previamente decenas de páginas; abrirla debe mostrar nombre, autor, fecha, foto y texto. La ocultación/moderación debe quitarla de ambas experiencias públicas y del listado de visibles en el CRM.


## 13. Selección múltiple, eliminación y acciones administrativas (norma reforzada)

1. **Toda pantalla de datos que admita acciones por registro debe permitir selección individual**, selección/deselección de la página visible y estado visual inequívoco. Los registros seleccionados se identifican por ID, no por índice de fila.
2. **La barra de acciones por lote aparece solo con elementos seleccionados**. Informa cantidad y alcance (página cargada frente a todos los resultados). La selección debe conservarse durante actualizaciones parciales, pero no aplicarse implícitamente a otros usuarios, tenants o consultas.
3. **La papelera se muestra únicamente si la entidad admite eliminación y el rol está autorizado por backend**. No es obligatorio ni apropiado mostrar una papelera de eliminación física sobre todas las entidades. La `×` de los filtros nunca borra registros.
4. **Eliminación lógica preferente:** publicaciones pasan a REMOVED; cuentas pasan por procedimientos de suspensión, cierre y eventual anonimización con retención legal, no `DELETE FROM users` arbitrario. Si hay dependencia con pagos, denuncias o mensajes se preservan integridad y pruebas.
5. **Acción sensible:** requiere motivo, confirmación con número de registros, MFA reciente, autorización de rol en servidor, auditoría de actor/objetivo/motivo/resultado, protección frente a duplicados y, según impacto, segunda aprobación. Toda operación debe mostrar éxito parcial/error sin perder selección.
6. **Usuarios:** selección múltiple habilitada para revisión, sin acción de borrado físico. La suspensión/cierre masivo se mantiene deshabilitada hasta una política explícita de aprobación y retención. No disfrazar la revisión como papelera.
7. **Un registro tiene ficha amplia** cuando sus datos son multisección o necesitan contexto para moderación. En esos casos la ficha ocupa pantalla completa o panel navegable, no un modal de un renglón.

## 14. Ficha de usuario — permisos y privacidad

La ficha desde `OGT Control → Usuarios` está organizada en **Resumen, Publicaciones, Denuncias y Chats denunciados**, en formato de pantalla completa adaptable.

| Sección | Datos y visibilidad |
|---|---|
| Resumen | Foto pública, nombre, rol, estado, puntos, ID interno, alta y última actividad para roles operativos autorizados |
| Publicaciones | Publicaciones VISIBLE del mismo backend público y medios asociados; consulta por páginas de 20, sin inspeccionar borradores ni mensajes |
| Denuncias de contenido | Solo OWNER y TRUST_SAFETY ven casos de `content_reports` referidos a publicaciones/comentarios del usuario; mínimo de datos y eventos de auditoría |
| Chats denunciados | **Bloqueado por defecto.** Solo se podrá abrir la conversación exacta cuando un participante haya presentado una denuncia autenticada que vincule su ID al chat y exista un expediente válido con alcance y auditoría |

**Condiciones para habilitar evidencia de chat** (no están cumplidas con el actual `content_reports`): la app debe permitir «Denunciar conversación» desde un chat concreto; el backend debe verificar que quien denuncia participa en ese chat; almacenar un expediente inmutable con `match_id`, denunciante, denunciado, causa, fecha y rango de mensajes permitido; el visor debe verificar OWNER/TRUST_SAFETY, MFA reciente, que el expediente siga abierto y registrar cada lectura. Jamás ofrecer «Ver todos los chats de este usuario». Evitar captura masiva o mensajes fuera de los límites del expediente. Sin ese vínculo, mostrar únicamente la restricción sin exponer mensajes.

La tarjeta de usuario no muestra correo, teléfono, UID de Firebase, ubicación ni conversaciones privadas por defecto. Las consultas del perfil quedan en `crm_audit_events`. No debe mostrarse una «eliminación» de cuentas que el servidor no admita explícitamente.
