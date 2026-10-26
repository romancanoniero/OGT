# OGT — Arranque iOS, feed y avatar (26/10/2026)

## Comportamiento corregido

1. El arranque siempre presenta una **splash breve** dedicada, sin botones ni
   carrusel. El isotipo oficial aparece por alas izquierda y derecha, con
   desplazamiento, giro de apertura, asentamiento amortiguado y punto final.
   La animación termina antes de continuar; no repite imágenes promocionales.
2. La autenticación Firebase se restaura tras la animación. Solo una sesión
   realmente restaurada puede entrar al feed; no se interpreta una excepción
   como un login válido. Si existe sesión y onboarding terminado, se abre
   directamente Feed (respetando deep links pendientes). Sin sesión se abre
   la presentación de invitados, cuyo título es **Only Good Things**, sobre
   **RED SOLIDARIA DE COMUNIDAD**, sin el sufijo '(OGT)'.
3. El feed en producción no debe mezclar publicaciones provenientes de
   caché local con una respuesta positiva de PostgreSQL. Los datos
   históricos no verificados no se pintan antes de la primera respuesta
   REST. La conexión sin respuesta no debe inventar publicaciones reales.
   No se borra la caché hasta confirmar procedencia y estado: las pruebas
   unitarias diferencian modo preview/fixture del feed verificado.
4. La foto del perfil se valida y sube con el token Firebase a
   `/api/v1/media/upload`; después se confirma su URL usando el endpoint
   autorizado `/api/v1/users/profile/photo`, que verifica que el archivo
   existe en el MediaStore de OGT y solo acepta imágenes. La foto aparece
   como guardada tras el éxito del servidor, nunca tras seleccionar una
   ruta efímera file://. La URL queda en users.photo_url de PostgreSQL,
   se recupera desde el perfil real al reiniciar y se presenta también en CRM.

## Pruebas imprescindibles antes de distribuir iOS

- iOS Simulator ARM64 y framework Kotlin compilados desde la misma revisión.
- Cerrar/abrir con sesión Firebase válida: splash → feed; sin landing.
- Cerrar/abrir sin sesión: splash → presentación; sin feed protegido.
- Cerrar/abrir con política biométrica: splash → desbloqueo/login.
- Seleccionar avatar, comprobar estado 'Guardando', resultado de API y que
  persiste tras terminar el proceso y tras reinstalar en otro dispositivo
  con la misma cuenta. Rechazo/timeout debe mostrar error, no falso éxito.
- Cargar feed y comparar ID de una publicación con
  `/api/v1/crm/platform/posts/search` bajo rol adecuado. Investigar
  diferencias por `moderation_status`, categoría, paginación, autorización
  y contenido anterior de la caché; nunca atribuir una captura a un ID sin
  encontrarlo en una API o en el dispositivo.

**Limitaciones:** estas correcciones en repo no demuestran que una publicación
específica haya desaparecido del backend; ese diagnóstico exige el ID
y una consulta autorizada al servidor. La subida a TestFlight exige
distribución firmada y validación del workflow iOS; una build del simulador
no es instalable en un iPhone físico.
