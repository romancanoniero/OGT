# OGT: regla permanente de publicación en Contabo

Vigencia: 25/10/2026. Repositorio exclusivo: `romancanoniero/OGT`.
Esta política, autorizada por el propietario, sustituye la activación por mensaje especial de commit y las instrucciones históricas de publicación exclusivamente manual de `FIRST_RELEASE.md`.

## Circuito obligatorio

Cada corrección se prepara en rama, se valida mediante PR y se integra en `main`. Cada push a `main` inicia `ogt-release.yml`; primero deben aprobarse `checks` y `browser`. Solo entonces se habilita `publish` en la instalación existente de Contabo. No se requiere un mensaje de commit especial ni pedir al usuario que pulse Run workflow.

Los PR, ramas `release/**`, etiquetas y otros repositorios nunca publican. La ejecución manual conserva las opciones `SOLO_COMPROBAR` y `PUBLICAR_PRIMERA_VERSION`; esta última solo publica desde `main` después de los mismos controles.

## Protecciones que se conservan

El entorno protegido sigue siendo `ogt-launch`, con sus secretos, restricciones y aprobaciones. No se eliminan revisores ni se eluden decisiones pendientes. Si hay aprobación obligatoria, se informa ese bloqueo concreto: iniciar automáticamente no significa saltarse la aprobación.

Se conserva la compilación y las pruebas de backend/shared, la comprobación SSH de identidad del servidor, los respaldos y las migraciones explícitas del activador existente. No se modifica `.env`, credenciales, permisos de cuentas, datos de usuarios ni la política de acceso público. No se autoriza por esta regla ninguna migración destructiva nueva ni restauración automática de la base de datos.

No se cancelan despliegues en curso para sustituirlos por otra versión. Quedan serializados por rama; solo `main` puede llegar al despliegue.

## Evidencia de entrega

No basta con un workflow verde: `publish` debe terminar exitosamente. Se verifica directamente en la VPS que `release.json` indique el SHA exacto del workflow, el repositorio correcto y el alcance `app-backend-crms`; además debe responder correctamente el endpoint de salud del backend.

Solo después de esa verificación se escribe el resumen de publicación y se conserva el manifiesto como artefacto `ogt-contabo-verified-<run>-<attempt>` durante 30 días. GitHub mantiene el historial del entorno y el resultado del job. No se crea un commit artificial de confirmación que pueda producir un bucle de publicaciones.

Un fallo de build o QA bloquea la publicación. Un fallo posterior a activar servicios exige revisar los registros y el procedimiento de recuperación existente; no se promete rollback automático de datos. `main` conserva los commits aunque el despliegue falle: código integrado y versión instalada son estados diferentes.

Quien realiza una corrección debe consultar los jobs y registros, resolver los errores y comunicar el commit, ejecución y estado real. No debe delegar en el usuario la lectura rutinaria de capturas ni declarar entrega a partir de checks aislados.

## Alcance de las pruebas

`qa/test_release_autopublish.py` verifica 240 combinaciones del evento, rama, repositorio, confirmación manual y resultado previo, además de la conservación de las protecciones y el orden de verificación. Se ejecuta dentro de la suite `test_release*.py` existente.

Las suites de navegador usan identidad/API simuladas. Un despliegue verificado no equivale a aceptación funcional completa con cuentas reales ni a pruebas de carga de fotografías en Android/iOS. Esas comprobaciones requieren pruebas autorizadas independientes.
