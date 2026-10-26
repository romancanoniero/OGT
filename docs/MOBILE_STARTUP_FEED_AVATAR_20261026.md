# OGT — arranque, feed y avatar persistente (26/10/2026)

## Evidencia y origen de la inconsistencia de Publicaciones

La captura de iOS muestra una publicación visible en la aplicación que no se
encuentra en **OGT Control → Publicaciones**. La inspección del código confirmó
un problema de arquitectura: `PublishActionScreen.publish()` generaba primero
un identificador local `post-pub-{timestamp}`, insertaba el contenido en memoria,
mostraba un aviso de éxito, y solo después intentaba la llamada a
`/api/v1/social/publish`. La respuesta remota se ignoraba cuando fallaba, por
lo que el teléfono conservaba un post inexistente en PostgreSQL. Además se
intentaba enviar URLs `file://` y `asset://`, que Contabo no puede servir.

`OgtPreviewSession` también restauraba `feedCacheJson` almacenado por el móvil,
con lo cual una publicación no confirmada podía reaparecer al abrir la app.
`CrmRoutes.kt` consulta exclusivamente `social_posts` del servidor con
`moderation_status = 'VISIBLE'`. **Son dos fuentes de verdad distintas.**
Sin el UUID de la publicación mostrada no es posible afirmar cuál fue el
registro exacto ni consultar su existencia individual en el servidor autenticado,
pero el defecto que permite esa discrepancia quedó localizado.

### Corrección implementada

1. La app sube primero imágenes reales a `/api/v1/media/upload`, usa la URL
   HTTPS confirmada y ejecuta la publicación en PostgreSQL.
2. Solamente después de obtener el `SocialPost` del servidor se muestra en el
   feed, se guarda en la caché y se anuncia por sockets. Los fallos mantienen
   el formulario abierto con un mensaje de error; no crean un post fantasma.
3. El feed operativo y la caché persistente solo aceptan UUID de publicaciones
   originadas en PostgreSQL, excluyendo explícitamente los IDs del catálogo mock
   y los `post-pub-*` antiguos.
4. Los fixtures siguen disponibles en el código para QA, pero **no como
   publicaciones de la comunidad en la aplicación de producción**. No se borra
   contenido del servidor ni se inventan publicaciones para llenar un feed vacío.

## Splash y sesión

La pantalla animada de apertura es independiente de la landing. El ala derecha
aparece primero, después la izquierda y finalmente el punto central. Se ejecuta
una sola vez por inicio. Tras validar el JWT de Firebase contra OGT, una sesión
válida navega directamente al Feed, sin presentar landing/onboarding. Si no
hay sesión, se muestra la landing con el nombre **Only Good Things** por encima
del lema **RED SOLIDARIA DE COMUNIDAD**, sin la sigla `(OGT)`. La configuración
biométrica no se elude. Los errores de sesión no se silencian como autenticación
válida.

## Foto de perfil

Antes, el picker llamaba `applyProfilePhoto(file://...)` y solo modificaba la
copia en memoria, que se perdía al reiniciar. Ahora la app sube el JPEG al
servidor, actualiza el perfil por la API autenticada y usa la URL devuelta **solo
después de recibir confirmación**. Al recuperar sesión, la foto de perfil
guardada en PostgreSQL tiene prioridad sobre la URL vieja de Firebase.

Los nombres y demás datos de usuario no se reescriben desde el dispositivo en
cada inicio. El backend no acepta rutas locales temporales como foto persistente.

## Validación y distribución

- `qa/test_mobile_production_contract.py`: contrato de arranque,
  publicación y foto.
- `shared/src/commonTest/.../ServerBackedFeedTest.kt`: exclusión
  de IDs de demostración y aceptación de UUID confirmados.
- `backend/src/test/.../OgtProfilePhotoUrlTest.kt`: URL de foto permitida.
- GitHub Actions ejecuta las pruebas y la compilación de simulador iOS en
  la rama protegida `main`. La distribución a TestFlight permanece
  manual y firmada por Apple, nunca automática sin las credenciales correctas.

**Límites:** la captura no contiene un identificador técnico del post y no
tenemos aquí una sesión autorizada de CRM con acceso a su base de datos.
La comprobación de esa publicación específica requiere recuperar su ID.
No se modifican las interfaces de OGT Empresas ni RetailNav.
