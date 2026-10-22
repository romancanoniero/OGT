# Publicar sin modificar un SDK con cambios locales

Este procedimiento agrega una alternativa explicita a PUBLISH_FROM_MAC.md:

    bash scripts/publish-ogt-from-mac.sh --deploy --sdk-committed

La opcion usa una copia local independiente de HEAD del SDK que ya existe en la
Mac. Incluye sus commits locales aunque aun no se hayan subido. NO incluye las
modificaciones sin commit, archivos staged, nuevos archivos sin seguimiento,
archivos ignorados, configuracion privada local ni credenciales del original.
No ejecuta stash, reset, clean, add ni commit sobre el SDK original. El SDK privado
no se sube al repositorio publico OGT. La copia temporal se elimina al terminar.

El modo habitual --deploy mantiene su comprobacion de SDK limpio. El nuevo modo
no significa que HEAD sea compatible o este probado: la compilacion y las pruebas
de backend/shared siguen siendo obligatorias antes de transferir y activar la
version. Si OGT depende de modificaciones pendientes del SDK, deben revisarse y
versionarse selectivamente antes de publicar; no guardar todos los archivos a
ciegas ni desactivar pruebas. Submodulos no soportados fallan explicitamente.

La preparacion privada del servidor conserva su comportamiento documentado:
puede ajustar los dos parametros de revision antes de compilar. Este cambio no
altera MFA, permisos, migraciones, respaldos ni el control de activacion.

Verificacion local: 12 pruebas con repositorios Git de prueba pasaron, incluidas
preservacion byte a byte del original (indice y archivos), commits locales,
HEAD separado, carpetas con espacios, archivos privados ignorados y origen.
No son una compilacion del SDK real, una ejecucion en la Mac ni un deploy a
Contabo. Consultar el resultado de CI de la revision concreta por separado.
