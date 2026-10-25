# OGT: primera version integrada, aun no abierta al publico

Este documento reemplaza el procedimiento de staging de interfaces. No se requiere
otro servidor, otra base, otro subdominio ni una cuenta Linux llamada ogt-staging.
Repositorio exclusivo: romancanoniero/OGT. Instalacion existente: ~/ogt.

## Alcance real

Una revision incluye la web, su SDK compilado, backend, ambos CRM y la migracion
CRM. Rutas previstas en el dominio existente:
- https://onlygoodthings.lat/
- https://onlygoodthings.lat/crm/control/
- https://onlygoodthings.lat/crm/empresas/

Son rutas de destino, no evidencia de que ya se haya publicado esta revision.
El codigo CRM integrado incluye sesiones, MFA y resumenes; los modulos operativos
completos y su aceptacion siguen pendientes. No confundir desplegar un candidato
privado con aprobar todos los requisitos de producto ni con abrir al publico.

## Acceso antes del lanzamiento

Se mantiene Firebase real, permisos por empresa y MFA; no se usan tokens de demo.
En el .env EXISTENTE del servidor, mantener las credenciales actuales y agregar:

    OGT_LAUNCH_MODE=prelaunch
    OGT_PRELAUNCH_CIDRS=<IP_PUBLICA_DEL_REVISOR>/32

El valor CIDR debe ser real y verificado, no copiar el marcador entre <>. Permite
varios rangos separados por coma. Traefik debe recibir directamente la conexion
TLS del revisor: no agregar rangos de un proxy/CDN compartido ni confiar en un
X-Forwarded-For suministrado por clientes. Si hay un proxy externo, revisar su
frontera de confianza antes del deploy. Los puertos 19080/19081 quedan vinculados
solo a 127.0.0.1 para que no se pueda saltar el control de entrada. Cambiar de red
puede requerir actualizar el CIDR. Esta restriccion es de la misma instalacion,
no una segunda instancia. La apertura publica necesita aprobacion y un cambio
explicito de politica; el script no la realiza.

## Desde la Mac ya autorizada

    bash scripts/deploy-ogt-vps.sh --check
    bash scripts/deploy-ogt-vps.sh --deploy

Por defecto se usa el alias SSH existente iankmp-vps y el SDK privado en
/Users/romancanoniero/AndroidStudioProjects/db-kmp-sdk. Se pueden indicar
OGT_VPS_HOST (alias SSH) y OGT_DB_KMP_SDK_PATH. Ambos repos deben estar limpios.
--check nunca publica ni se conecta por SSH. --deploy ejecuta tests del backend
y shared y compila backend/SDK; un error impide continuar. No es necesario cargar
secretos nuevos en GitHub cuando se ejecuta desde esa Mac.

## GitHub Actions (alternativa sin depender de la Mac)

Workflow: OGT - primera version integrada, siempre desde main. Los pushes y PR
solo ejecutan comprobaciones, nunca publican. Para publicar, workflow_dispatch
con PUBLICAR_PRIMERA_VERSION y el entorno de autorizacion ogt-launch. Ese entorno
es un contenedor de permisos de GitHub; no crea otra instalacion en Contabo.

Configurar una vez sus secretos:
- OGT_VPS_HOST: direccion SSH de la VPS existente.
- OGT_VPS_USER: usuario autorizado a administrar SOLO la instalacion de OGT.
- OGT_VPS_SSH_KEY: identidad autorizada de despliegue, nunca una clave publicada.
- OGT_VPS_KNOWN_HOSTS: identidad del servidor previamente verificada.
- OGT_SDK_READ_TOKEN: token limitado a lectura del repositorio privado db-kmp-sdk.

Variables: OGT_VPS_SSH_PORT (opcional, 22 por defecto) y OGT_SDK_REVISION
(commit completo del SDK ya verificado). El token del SDK no es necesario en la
Mac si el repositorio ya esta disponible. No copiar el SDK privado al repo publico.
Conservar revisores requeridos y limitar el entorno a main. No desactivar esos
controles para evitar una aprobacion. No se utilizan valores OGT_STAGING_*.

## Proteccion de datos y despliegue

El activador exige la instalacion existente saludable y las tablas previas; no
inicializa una nueva DB ni adivina migraciones ausentes. La revision inicial solo
aplica 23_crm_foundations.sql, aditiva e idempotente. Nunca vuelve a ejecutar los
SQL historicos con usuarios y conversaciones de demostracion. Los datos demo que
ya existan no se eliminan automaticamente: requieren una revision separada.

Antes del cambio se compila una imagen candidata, se valida nginx y Compose,
se guardan los archivos previos y la imagen anterior, se detienen web/API y se
obtiene un pg_dump custom verificado con pg_restore --list. La migracion usa
ON_ERROR_STOP y una transaccion. Despues se publica todo el paquete y se verifica
salud, revision exacta, ambas paginas, rechazo de peticiones anonimas y puertos.
No se modifica .env, media, credenciales de Firebase, datos de Redis o volumen DB.
El manifiesto registra checksums de TODOS los archivos y la revision del SDK.

## Fallos y recuperacion

No hay promesa de rollback de datos automatico. Ante un fallo despues de detener
los servicios, web/API permanecen detenidos para no servir versiones mezcladas.
Material de recuperacion: ~/ogt/backups/<release-id>/application.tgz,
database.dump, database-toc.txt y previous-image.txt. La imagen anterior queda
etiquetada como ogt-backend:before-<release-id>. Un operador debe revisar la fase
de fallo antes de restaurar archivos/imagen y, solo si corresponde, la DB. No
usar docker compose down -v ni restaurar un dump sobre datos nuevos sin revision.
No se ha ensayado todavia esta recuperacion en el servidor real.

## Condiciones para dar por terminado el lanzamiento

CI de fuentes/browser, build/test backend, migracion real, respaldo/recuperacion,
HTTPS desde la red permitida, MFA real, cuentas autorizadas, permisos negativos
entre empresas y funciones de negocio deben verificarse por separado. Los tests
de navegador usan mocks. Un job verde de fuentes no certifica el servidor.

## Solicitud de publicación del CRM Control (25/10/2026)

Versión base integrada en `main`: `b28e51a39c16e936f848d9a5d424442c4778a069`.
Incluye ordenamiento global por fecha, autor y texto, y paginación de 20 publicaciones.
La implementación de GitHub Actions ejecuta validaciones de código y navegador antes de activar
la publicación integral de web, backend y CRM en la instalación existente.
La publicación se considera confirmada solamente si el job `publish` verifica en Contabo
el SHA exacto de la compilación y un backend saludable; los checks aislados no la prueban.

## Publicación de filtros compactos y orden acumulativo (25/10/2026)

El CRM Control incluye búsqueda combinada con filtros acumulativos por tema, autor,
presencia de fotografía y fechas, y ordenamiento por prioridades (fecha, autor y texto).
Las opciones se presentan en paneles compactos con etiquetas que pueden quitarse
individualmente. El backend pagina en lotes de veinte con cursor v3 ligado a todos
los filtros y órdenes para que el orden sea estable al cargar más resultados.

La versión integrada antes de activar el release fue
`b6e19a44fdbe34ec33027395cc8c65221a831d51`.
El commit de release debe superar código, QA de navegador, compilación de backend,
respaldo y verificación de revisión exacta en la instalación existente de Contabo.


## Actualización parcial del listado de Control (25/10/2026)

Integrado en main: `e3e4639533597b558695d52937107de9c8bda484`.
El refresco de publicaciones ya no desmonta la barra de búsqueda, filtros y
orden. Reconcilia filas por identificador, conserva las filas sin modificaciones
y la posición horizontal mientras cambia la búsqueda, el orden o un filtro.
Mantiene los resultados previos visibles durante la solicitud y evita saltos
de pantalla al cargar lotes adicionales. El workflow de navegador comprueba la
identidad de los nodos entre actualizaciones con una API simulada.

Este commit de release incluye ese cambio y debe verificarse por su SHA instalado
en Contabo; una ejecución verde de checks aislados no implica despliegue.

## Estándar de datos y pantalla Usuarios de OGT Control (25/10/2026)

Integración inicial: `e18f8c35345ecfd44145da6bcbc3f3217a68a602`.
El estándar obligatorio de listas de datos está en `docs/DATA_VIEWS_STANDARD.md`.
La sección Usuarios pasa de métrica agregada a listado real de solo lectura,
con búsqueda por nombre/ID, filtros acumulativos por estado, rol y fecha de
alta, ordenamiento multinivel, chips compactos y paginación de 20 usuarios.
La respuesta no contiene contactos ni identificadores Firebase y la API exige
rol operativo autorizado por el backend. No se incorporan botones de eliminación
o suspensión de cuentas. Los filtros/órdenes se recuerdan al volver a la sección.

El release exige checks, regresión de navegador, compilación de backend y
verificación en la VPS del mismo SHA; hasta que `publish` marque success
no se puede afirmar que Contabo haya sido actualizado.
