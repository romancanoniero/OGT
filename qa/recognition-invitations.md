# Categorías, nombres e invitaciones a publicaciones

Implementación del recorrido solicitado. El despliegue conserva las publicaciones, cuentas y controles de release existentes; no requiere mover carpetas ni reiniciar datos de las apps. El estado instalado se registra en la evidencia de despliegue.

## Comportamiento

- Control puede crear, ordenar, renombrar y desactivar categorías desde **Categorías de reconocimiento**, con los permisos vigentes de OWNER u OPERATIONS y segundo factor. Cada cambio queda auditado. Desactivar impide nuevas publicaciones con esa categoría y conserva las existentes.
- En **Dar a conocer**, el nombre completo que aparece en la publicación se escribe separado de la cuenta @nick opcional. El @nick es único y se guarda desde Configuración. Aceptar el reconocimiento vincula la cuenta y conserva el nombre completo escrito en el post, aunque el perfil use otro nombre. Las cuentas anteriores conservan su nombre y siguen siendo localizables.
- Se pueden preparar invitaciones al publicar y añadir más desde el post propio. La persona reconocida y las personas invitadas a conversar tienen propósitos distintos: aceptar una invitación a participar no atribuye la buena acción.
- OGT guarda el post y la invitación antes de abrir las aplicaciones de envío. **Compartir** abre el selector nativo; email y SMS permiten preparar un mensaje con destinatario. El usuario confirma el envío en la aplicación elegida. Cancelar no elimina el post ni la invitación.
- El usuario asume lo que cobre su plan por SMS o datos. OGT no compra SMS ni envía emails/SMS automáticamente. No se necesitan cuentas, claves ni integraciones con Brevo, Twilio o Resend para este recorrido. Los avisos internos usan la infraestructura de notificaciones existente.
- El enlace permite entrar o registrarse y volver al mismo post. Android conserva el token durante el acceso, la preparación del perfil y los permisos, y admite el referrer de Play. En iPhone, después de instalar se debe volver a tocar el enlace recibido; no se promete continuidad automática a través de App Store.
- Cada enlace vence a los 30 días. Quien publica puede retirar una invitación pendiente. Las invitaciones por correo/teléfono exigen una cuenta con ese dato; las dirigidas a una cuenta exigen esa cuenta. Un enlace genérico para compartir es personal y debe enviarse al destinatario previsto. No se puede aceptar un reconocimiento ya reclamado por otra cuenta.
- El CRM informa invitaciones creadas, hojas de compartir abiertas, enlaces abiertos, registros y aceptaciones. Abrir compartir no se presenta como entrega del mensaje. Contactos y tokens no se muestran en esos paneles ni en la consulta pública de invitación.

Los homenajes y sus enlaces `/h/`, ternuras, convocatorias, publicaciones históricas, comentarios, mascotas, trueque, estacionamiento, sesión persistente y permisos conservan sus recorridos. El nuevo enlace utiliza `/i/` y no sustituye los anteriores.

## Activación

1. Aplicar **39_recognition_invitations.sql** y **40_gathering_details.sql** mediante el procedimiento habitual de release, antes de iniciar el nuevo servidor. La migración es aditiva; no reescribe posts o nombres históricos.
2. Publicar servidor y web coordinadamente. Incluir el cambio de nginx para `/i/`, los módulos del CRM y las asociaciones de enlaces de Android/iOS. Si se publica el sitio de enlaces Firebase, incluir su nueva regla `/i/**`.
3. Configurar la URL real de App Store con `OGT_IOS_STORE_URL` o `OGT_IOS_APP_ID` cuando esté disponible. No hace falta configurar proveedores de email/SMS.
4. Distribuir las versiones móviles actualizadas. El formulario nuevo depende del catálogo y los endpoints del servidor actualizado; no instalarlo sobre un servidor anterior a la migración.

La activación se considera completada únicamente al verificar la revisión del servidor, salud, migraciones y aplicación actualizada. La evidencia de esta activación se guarda en `qa/artifacts/invitation-gathering-deployment.json`.

## Validación local

La base utilizada fue `ogt_business_qa_growth20261125`, en loopback. Autenticación y mensajes se probaron con identidades sintéticas del emulador Firebase; no se enviaron mensajes a personas reales. El servidor de QA ejecuta una copia independiente del runtime para que recompilar no cambie archivos de un proceso en uso.

- 82 pruebas del servidor, 165 de lógica compartida y 110 pruebas portables de Android.
- 5 pruebas de integración de reconocimientos e invitaciones: publicación atómica, reintentos, categorías inactivas, permisos de autor, cuentas destinatarias, email/teléfono, aceptación, rechazo, retirada y vencimiento.
- 113 pruebas de módulos web existentes.
- Selector del sistema probado en Pixel_6a_4 con el paquete aislado `com.onlygoodthings.app.qa`: se interceptó la apertura de los compositores de compartir, email y SMS y se cancelaron; no hubo envíos.
- Compilación de Android e iOS Simulator.
- 18 regresiones de navegador en Chromium y WebKit móvil: sesión, foto de perfil, publicaciones, comentarios, reconocimiento de otra cuenta, mascotas, trueque y estacionamiento.
- Convocatoria con su formulario propio, fecha y lugar, comprobada en ambos navegadores.
- Recorrido nuevo de invitaciones en ambos navegadores: categoría desde CRM, nombre completo, invitación, cancelación de compartir, acceso anónimo, registro, aceptación, retorno al post, aviso a cuenta registrada e invitación adicional desde el post. Incluye rechazo de acceso CRM sin segundo factor y conservación de publicaciones al desactivar la categoría.

Pruebas principales: `qa/tests/post-invitations.spec.mjs`, `backend/src/test/kotlin/com/onlygoodthings/backend/social/PostInvitationIntegrationTest.kt`, `shared/src/commonTest/kotlin/com/onlygoodthings/shared/domain/RecognitionInvitationTest.kt` y `composeApp/src/androidInstrumentedTest/kotlin/com/onlygoodthings/app/platform/PostInvitationShareTest.kt`.
