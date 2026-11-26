# Activación contextual de ubicación — 26/11/2026

## Problema observado

En Pixel_6a_3, Android tenía la ubicación encendida y los permisos concedidos, pero el interruptor GPS de OGT estaba apagado. No había coordenadas ni solicitudes activas de OGT. Estacionamiento mostraba «Sin señal GPS» y «Reintentar» llamaba a una obtención que, correctamente, no funcionaba con el seguimiento desactivado. Faltaba guiar la activación de la app.

## Diferencias intencionales

- Al entrar a Estacionamiento sin un auto guardado, o a su confirmación, se ofrece el aviso de ubicación existente si GPS está apagado en OGT. «Ahora no» o Atrás lo cierra y no se vuelve a abrir por recomposiciones de esa entrada.
- El aviso conserva la explicación de uso y transferencia de ubicación, las opciones «Mientras uses la app» y «Siempre», y la confirmación explícita. No se activa el seguimiento al mostrarlo.
- Después de confirmar, se resuelve primero el permiso y después la ubicación del sistema. Si falta permiso se solicita; si quedó bloqueado se ofrece Ajustes mediante una acción del usuario. Android utiliza su resolución nativa para encender la ubicación; iOS conserva la apertura de Ajustes existente.
- Para estacionar en primer plano alcanza «Mientras uses la app». «Siempre» se solicita cuando el usuario lo elige explícitamente, y el seguimiento en segundo plano existente conserva su condición de autorización.
- El aviso distingue GPS apagado en OGT, permiso faltante/bloqueado, ubicación del sistema apagada, búsqueda en curso y ausencia real de señal. «Activar ubicación» permite retomar el recorrido después de rechazarlo.
- «Estoy acá» y «Usar mi ubicación» comprueban esos requisitos antes de esperar una coordenada. La ausencia de señal con permisos y ubicación activos no vuelve a abrir el aviso de consentimiento.

## Comportamiento protegido

Ingresar una dirección o elegir un punto manualmente sigue disponible sin activar GPS. Consultar un auto ya guardado no fuerza el aviso. No se cambian frecuencias de GPS, detección de maniobras, sincronización, notificaciones, contratos del servidor, permisos declarados, datos ni integración nativa de Android/iOS. No se agregan sondeos periódicos.

## Verificación

- Android: `assembleDebug` y `testDebugUnitTest` correctos; 145 pruebas sin fallos. Incluye diez pruebas nuevas de requisitos, orden permiso/sistema, consentimiento, bloqueo, alcance y rutas, además de las regresiones de ubicación, estacionamiento y feed existentes. Shared: 168 pruebas sin fallos.
- iOS: `compileKotlinIosSimulatorArm64` correcto. Se genera además el archivo Release para iPhone en `build/ios-gps-activation/OGT.xcarchive` y el IPA en `build/ios-gps-activation/export/iosApp.ipa`; el resultado de generación, firma y recursos queda registrado en `qa/artifacts/gps-activation.json`.
- Pixel_6a_3 actualizado sin reinstalar desde cero: sesión y feed conservados. Se comprueba visualmente el aviso al entrar, «Ahora no», permanencia del aviso correcto sin reapertura automática, formulario manual por dirección y reapertura mediante «Activar ubicación».
- La prueba manual no pulsa «Continuar», no concede permisos, no enciende GPS ni inyecta coordenadas; deja la elección al usuario. No se publica contenido ni se guarda un auto. Pixel_6a_4 permanece pendiente y sin modificar. La generación de iOS no equivale a una prueba funcional de GPS en un iPhone físico.

Los artefactos y logs son locales y están excluidos de Git. No se despliega al servidor ni se publica en Apple.
