# Operación privada de OGT

El estado se consulta en Control → Estado del servicio, con correo verificado,
MFA vigente y rol OWNER u OPERATIONS. Cada consulta vuelve a validar permisos.
Empresas y los restantes roles de Control no reciben estos datos.

## Supervisión

El recolector comprueba cada minuto, con tiempos máximos y solo lecturas:

- Los cuatro servicios de OGT, aplicación, disponibilidad de pagos y Redis.
- Base de datos, confirmaciones de pago atrasadas, incidencias de pagos,
  bajas de cuentas y eliminaciones de archivos pendientes.
- Coincidencia entre versión instalada y servida, certificado HTTPS y capacidad.
- Rechazo de consultas anónimas a los CRM y antigüedad de los respaldos.

Una lectura fallida ya se muestra como advertencia. Tres fallos consecutivos
producen un incidente; una consulta anónima que devuelva éxito es un incidente
inmediato. Un archivo con más de tres minutos sin actualizar se muestra vencido,
no saludable. Memoria disponible inferior al 10 % y disco usado superior al 85 %
producen alertas; menos del 5 % de memoria, más del 90 % de disco o menos de 2 GiB
libres fallan la comprobación de capacidad. HTTPS debe conservar al menos 14 días.
La vista se actualiza cada 30 segundos mientras está visible.

Los archivos `~/ogt/operations/status.json`, `metrics.prom` y `events.log` son
privados y contienen estados agregados, sin usuarios, claves ni errores SQL.
El formato de métricas permite una integración posterior; no se modifica la
supervisión compartida con otras aplicaciones. Las alertas se registran y se
muestran en Control. No se envían correos, SMS ni mensajes externos automáticamente.

El instalador conserva cada tarea previa del crontab y respalda su versión
anterior antes de agregar exclusivamente el bloque OGT. Una actualización solo
sustituye ese bloque. No necesita cambiar permisos del sistema ni otras aplicaciones.
El recolector no reinicia servicios ni reintenta pagos por cuenta propia.

## Respaldo diario de la base

Una tarea horaria comprueba si corresponde un respaldo nuevo, como máximo uno
cada 24 horas. Usa `pg_dump -Fc`, verifica el archivo con `pg_restore --list` y
publica el recibo solo después de completar ambas operaciones. El destino es
`~/ogt/backups/scheduled-<fecha UTC>/`, privado. No borra respaldos anteriores,
no incluye claves y no restaura datos automáticamente. Exige espacio suficiente
antes de empezar. La supervisión advierte si no existe un respaldo verificado
con menos de 48 horas de antigüedad.

Este respaldo diario cubre PostgreSQL. La copia de medios y la copia externa al
servidor siguen siendo requisitos separados de recuperación ante pérdida de la
VPS. Tampoco reemplaza la política de retención que debe definir el responsable.

## Recuperación de la aplicación

`restore_ogt_application.py` imprime primero un plan. `--apply` requiere el SHA
actual esperado, una versión sellada de destino y un fundamento. Comparte el
candado del despliegue, rechaza cambios locales inesperados y exige contratos de
migración idénticos: una diferencia de esquema exige revisión independiente.
Verifica también la conservación de puertos privados, red, volúmenes, credenciales,
servicios de datos y control de acceso. La imagen de destino debe existir.

Ejemplo para ejecutar en la instalación existente, sustituyendo identificadores
por los del manifiesto y del directorio de versiones:

```sh
python3 ~/ogt/releases/<versión con la herramienta>/scripts/restore_ogt_application.py \
  --target-release <versión de destino> --expected-current-sha <SHA actual>
```

Después de revisar el plan, se ejecuta el mismo comando con
`--apply --reason 'Motivo concreto de la recuperación'`.

Todos los archivos se preparan antes de detener web/API. Se guardan la aplicación
y la imagen previas; solo se sustituyen backend, web y su configuración sellada.
No se ejecuta SQL, no se restaura PostgreSQL, no se reinicia Redis/PostgreSQL y no
se modifican `.env`, credenciales o medios. Se verifican salud, SHA, ambos CRM,
rechazo de consultas anónimas y hashes antes de confirmar la recuperación.
Un fallo durante la activación conserva los servicios detenidos y deja un recibo
privado con la fase y los archivos movidos. La revisión humana debe resolverlo;
no se sobrescriben datos recientes con un respaldo antiguo.

## Ensayo y límites de aceptación

El modo de laboratorio solo permite directorios temporales con prefijo fijo,
contenedores etiquetados y puertos de loopback. Usa una base y Redis propios,
red interna para backend/datos y un frente web publicado solo en loopback.
Usa Firebase Auth emulado con identidades y claims de verificación/MFA sintéticos;
no utiliza cuentas de Firebase ni pagos reales. Este ensayo no sustituye la
aceptación humana del ingreso/MFA con proveedores reales.
El ensayo cambia de candidata a anterior y vuelve a candidata, comprobando
campañas, permisos, reservas, conciliación, reintentos idempotentes y escrituras
realizadas mientras estaba activa la versión anterior. Retira únicamente los
recursos que creó y que conservan su etiqueta única.

`--prepare` del publicador compila, valida y sube el paquete sin activar servicios;
permite completar el ensayo antes de ejecutar el activador sellado. `--deploy`
conserva su recorrido previo y publica la revisión completa.

La medición local de carga usa solo la API de QA, PostGIS/Redis separados y
Firebase emulado. No constituye una garantía de capacidad de la VPS, de cargas
de video o de proveedores externos. Antes de abrir al público deben resolverse
las alertas de capacidad reales y acordarse el volumen esperado de uso.


La aceptación del 28/10/2026 se realizó en Docker local, porque la VPS compartida
tenía menos del 5 % de memoria disponible. Las imágenes se construyeron con los
JAR sellados de `39a7bcd` y `d564257`; solo la configuración del laboratorio cambia
red, autenticación emulada y puertos. El recorrido candidata → anterior → candidata
conservó campañas y titularidad, una reserva inicial de 3.000 centavos, su reintento
sin duplicación y otra reserva de 1.000 centavos realizada en la versión anterior.
El saldo final fue 46.000 disponibles y 4.000 reservados, conciliado. No se restauró
la base durante este recorrido y los recursos temporales fueron retirados.

El respaldo programado `scheduled-20261028T211719Z` también fue restaurado en una
base temporal separada: coincidieron 14 usuarios, 42 publicaciones, dos empresas,
dos membresías, tres asientos contables y 64 tablas. La base atendida por la app
conservó su estado y la copia temporal fue eliminada. Son dos comprobaciones
diferentes: recuperación de aplicación con datos recientes y restauración del
archivo de respaldo. Los recibos privados están en `build/operations-*.json`.

## Bajas de cuentas después de una copia de datos

El agente de respaldo de la Mac conserva además `erasure-journal.aesgcm` en el
destino aprobado de DEV_MAC. Cada ejecución horaria copia únicamente las huellas
SHA-256 de identidades dadas de baja, sin correos, UID ni claves de sesión. Cifra
este registro con la clave local existente y autenticación AES-256-GCM. Su unión
es acumulativa: restaurar una base antigua en el origen no puede retirar bajas
que ya se habían registrado fuera del servidor. La retención de 30 días de las
copias completas se mantiene. Un fallo del registro genera una advertencia y no
impide realizar la copia diaria de datos y archivos.

Este registro horario no garantiza cubrir las bajas ocurridas después de su
última captura. Antes de recuperar datos hay que impedir nuevas escrituras en
el origen y obtener un registro que cubra su última escritura posible. Si el
origen se perdió y no existe esa evidencia, la candidata no puede reabrirse
automáticamente: debe resolverse ese intervalo antes de servirla.

La restauración se prepara en una base **sin tráfico**, con nombre
`ogt_recovery_<32 caracteres hexadecimales>` y comentario
`ogt-recovery-privacy:<los mismos 32 caracteres>`. Nunca se restaura directamente
sobre `onlygoodthings`. Tras restaurar y revisar la copia se ejecuta primero un
plan desde la Mac, con tiempos UTC en segundos Unix:

```sh
python3 scripts/ogt_recovery_privacy.py \
  --encrypted-journal /Volumes/DEV_MAC/OGT-Backups/data/erasure-journal.aesgcm \
  --key-file /Users/romancanoniero/.config/ogt/secure/backups/data-aes256.key \
  --database ogt_recovery_<identificador> \
  --snapshot-epoch <instante de la copia> \
  --required-through-epoch <última escritura posible del origen> \
  --receipt <ruta privada del recibo>
```

Después de revisar ese plan, `--apply` ejecuta exclusivamente en esa base temporal
el mismo recorrido de baja de OGT: elimina contenido propio, conserva y vuelve
a contar las conversaciones ajenas y deja la limpieza de archivos en la cola
existente. Bloquea conflictos con titulares de empresas o cuentas de Control;
primero hay que resolver su titularidad. Rechaza otras conexiones activas y
registros sin cobertura temporal o con huellas alteradas. Puede reintentarse.
No contacta Firebase, no cambia archivos, rutas públicas ni la base principal.
Un fallo puede dejar bajas ya reconciliadas en la candidata, que debe permanecer
sin tráfico hasta completar el recorrido. El recibo nunca autoriza por sí solo
la apertura: quedan las comprobaciones de medios, permisos, salud y aprobación
de la recuperación. El CLI no tiene un endpoint HTTP.

El ensayo local `qa/scripts/verify_recovery_privacy.py` usa datos sintéticos en un
contenedor etiquetado, con PostgreSQL publicado solo en loopback. Comprueba plan,
rechazos, baja, contadores, limpieza en cola e idempotencia; compara la base de
origen antes y después y retira únicamente el contenedor creado para el ensayo.
