# OGT: pruebas automáticas y evidencia

Repositorio exclusivo: `romancanoniero/OGT`.

## Regla permanente

Cada push a `main` o `release/**`, y cada pull request dirigido a `main`, ejecuta `.github/workflows/ogt-release.yml` automáticamente. No se requiere que el usuario abra el navegador, pulse Run workflow o envíe capturas para iniciar las pruebas. Quien realiza la corrección debe consultar el estado y los registros de GitHub, corregir el fallo y validar de nuevo antes de declarar terminada la entrega.

## Orden de validación

1. `checks`: compilar la sintaxis de todos los Python de `qa/` y `scripts/` sin ejecutarlos ni generar pyc; pruebas del ejecutor QA; validaciones existentes de JavaScript, seguridad y release; sintaxis de shell.
2. `browser`: instalar Playwright 1.56.0 y Chromium; ejecutar `qa/crm-browser.py` y `qa/crm-entrypoints-browser.py`. Aunque falle la primera suite, la segunda se ejecuta para no ocultar otros fallos. Cualquier fallo o timeout hace fallar el job y bloquea `publish`.
3. Guardar registros por suite, `results.json`, `junit.xml` y un resumen en GitHub Actions. Los artefactos `ogt-browser-qa-<run>-<attempt>` se conservan 7 días, incluso cuando las pruebas fallan. Si falla la instalación antes de iniciar las suites, el registro del paso de instalación es la evidencia; no se inventa un resultado de pruebas.

No se reintentan automáticamente aserciones hasta obtener verde. Los errores de código requieren una corrección. No se usa `continue-on-error` para eludir las validaciones. Las aserciones de Python permanecen habilitadas.

## Ejecutar el mismo circuito localmente

```bash
python qa/run_automated_tests.py --syntax
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s qa -p 'test_qa_automation.py' -v
python -m pip install 'playwright==1.56.0'
python -m playwright install --with-deps chromium
python qa/run_automated_tests.py --browser --output /tmp/ogt-qa-results
```

Las dependencias se instalan en el entorno de desarrollo o en un entorno virtual. Los informes se escriben fuera del checkout para respetar la comprobación de árbol limpio del proceso de release.

## Imágenes de perfil

El test comprueba foto inicial, circularidad, sombra, ubicación después del selector, imagen ausente/rota, cambio real de versión en la API simulada, refresco del listado y coherencia de la ficha. El cambio de la fixture debe preceder a la consulta; no se permite reemplazar la expectativa nueva por la vieja para ocultar un error. Navegación y barra de herramientas deben conservar sus nodos durante el refresco.

## Límites y publicación

Estas suites usan identidad y API simuladas. No certifican por sí solas Firebase real, persistencia de fotografías en el backend, cargas desde Android/iOS ni una instalación en Contabo. Las pruebas reales de esos flujos requieren un entorno autorizado separado; nunca se cargan credenciales productivas en las pruebas de PR.

Este cambio automatiza y refuerza QA, pero **no modifica la condición de autorización de `publish`, los secretos ni las aprobaciones de `ogt-launch`**. Un workflow verde con `publish` omitido no es un despliegue. La confirmación de Contabo exige un `publish` exitoso, SHA instalado coincidente y backend saludable. La automatización de pruebas no implica autorreparación de código ni publicación automática de toda corrección.
