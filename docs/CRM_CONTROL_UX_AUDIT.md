# OGT Control — Auditoría visual y funcional automatizada

**Alcance:** exclusivamente el CRM web OGT Control del repositorio `romancanoniero/OGT`.
Las aplicaciones Android/iOS y OGT Empresas no se modifican ni se inspeccionan en
esta auditoría. No realiza cambios de datos reales ni inicia sesiones de usuarios.

## Qué revisa automáticamente

Cada ejecución abre Chromium con los componentes CSS/JS reales del CRM, montados
con cuentas, datos y permisos **simulados**. Recorre Resumen, Usuarios, Empresas,
Denuncias y Publicaciones con anchos 1440, 1024, 900 y 390 px.

- Navegación: una sola sección activa, cinco entradas, iconografía semántica
  esperada (por ejemplo, figura humana para Usuarios) y ningún ícono roto.
- Geometría: documento sin desbordamiento accidental, panel dentro de la
  pantalla, tablas con scroll interno y tarjetas redondeadas en escritorio.
- Interacción: botones con nombre accesible, acciones por íconos y tamaño de
  clic adecuado. En Usuarios revisa selección múltiple, bloqueo, eliminación y
  sus confirmaciones modales; ningún clic sobre Cancelar o Esc debe ejecutar una
  mutación.
- Confirmaciones: diálogo en primer plano real (`showModal()`), bordes redondeados,
  sin recorte, botón Cancelar visible.
- Calidad de contenido: avisos de textos técnicos o de relleno (p. ej., valores
  `undefined` en encabezados) y etiquetas confusas que cumplen reglas
  expresamente codificadas.

El recorrido produce **al menos 20 capturas** (cinco secciones en cuatro
resoluciones), más estados con usuarios seleccionados, filtros, orden, ficha
de usuario y diálogos de confirmación. Las capturas son de una sesión ficticia,
nunca muestran datos privados de producción.

## Cuándo corre y dónde ver resultados

1. **Por cada PR y cada push a main:** el job de navegador del workflow
   `.github/workflows/ogt-release.yml` incluye el auditor como una de sus suites.
   Los errores bloqueantes impiden la publicación; las advertencias quedan
   registradas sin hacer pasar un error como éxito.
2. **Semanalmente, lunes 08:00 Buenos Aires (11:00 UTC):** el workflow
   `.github/workflows/ogt-control-ux-audit.yml` ejecuta solo la auditoría, sin
   recompilar el backend, sin despliegue y sin claves de acceso a Contabo.
3. **Ejecución manual:** GitHub Actions → OGT Control - auditoria visual automatica
   → Run workflow.

Los artefactos de cada ejecución contienen
`control-ux-audit/report.md`, `findings.json` y capturas PNG nombradas por
resolución y pantalla. Los hallazgos tienen **error** (violación verificable que
falla CI) y **warning** (aspecto que requiere revisión humana).

Para ejecutarla en un entorno de desarrollo con Playwright y Chromium instalados:

```bash
python qa/run_automated_tests.py --ux --output ./qa-results
```

## Criterios y límites

**Detectable por reglas:** desbordamiento, imágenes inexistentes, mal icono
de navegación, acciones sin nombre, controles pequeños, diálogos cuadrados o
tapados, errores de JavaScript y fallas de recorrido.

**Revisión visual complementaria:** alineaciones que no violan una geometría
obligatoria, exceso de espacio, distribución desagradable, íconos que parecen
inapropiados pero no tienen regla declarada, información poco pertinente,
jerarquía visual, tono o densidad. Las capturas permiten analizar estos casos,
pero **no afirmamos que un algoritmo geométrico tenga criterio estético humano
ni que encuentre cualquier inconsistencia**.

Para contrastar información real, autorizaciones efectivas y segunda
autenticación de Firebase se necesita una prueba E2E distinta con credenciales
de una cuenta de pruebas y sin acceso a datos privados innecesarios. Esta
auditoría no evita ni simula un inicio de sesión válido en producción.

**Política de mantenimiento:** cuando se aprueba un patrón visual nuevo se
añade a `docs/DATA_VIEWS_STANDARD.md` y se incorpora una aserción específica
a esta suite. No añadir reglas que generen falsos positivos o que eleven
automáticamente recomendaciones subjetivas a errores bloqueantes.
