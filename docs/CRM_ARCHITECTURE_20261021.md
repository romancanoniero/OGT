# OGT CRM — contratos de producto y seguridad (21/10/2026)

Estado: especificación y primera corrección de seguridad en rama de trabajo. **No** significa que los dos CRM estén completos ni desplegados.

## Dos productos, arquitectura común

**OGT Control (administración central).** Dashboard operativo; usuarios (estado, búsqueda, historial, exportación/borrado, consentimiento, bloqueos, reclamos); empresas (alta, verificación, titulares y equipo, suspensión); contenido/denuncias (colas, evidencia, apelaciones y doble revisión); campañas/RSE, presupuestos, conciliación, cupones; catálogo/taxonomías multilingüe; reglas de moderación/IA; geografía y parámetros configurables; auditoría e incidentes; configuraciones/versiones; informes, exportaciones, integraciones, operaciones y alertas. Nunca mostrar credenciales ni datos personales más allá de lo necesario para el rol.

**OGT Empresas (tenant).** Dashboard con KPIs; identidad/compliance; miembros y permisos; campañas (borrador, revisión, publicación, pausa, cierre), objetivos y presupuesto; cupones (límites, emisión, canje, revocación, prevención de duplicados); impacto verificable; historial financiero y conciliación; CRM de campañas/comunidad con consentimiento, sin exportación irrestricta de datos ajenos; reportes y facturación; avisos, soporte, actividad y ajustes. Un operador empresarial no accede a otra empresa por cambiar un ID.

## Seguridad base

1. Aislamiento por tenant en **cada query**, no solo en front; roles y permisos de mínimo privilegio por operación; super-admin y staff separados de COMPANY_ADMIN/COMMUNITY_MODERATOR.
2. Nunca conceder derechos privilegiados por reclamar un UID ni por un valor arbitrario de Firebase; asignaciones administrativas mediante bootstrap fuera de banda y tabla auditable. Suspensión/baja debe invalidar acceso de backend.
3. Acciones sensibles: autenticación reciente/MFA resistente a phishing en staff, confirmación, trazabilidad inmutable de actor/tenant/objeto/motivo y aprobación dual cuando corresponda.
4. Mutaciones monetarias/cupones: idempotencia, transacciones, límites, conciliación, prohibición de saldo negativo, y control de estados.
5. Evitar exposición de PII: minimización, máscaras, exportación controlada, retención, derechos de datos, logging sin secretos.
6. Validación server-side, paginación acotada y filtros, rate limiting, protección XSS/CSRF/CORS, uploads y WebSocket autorizados.
7. Separar datos demo/staging/prod, migraciones reversibles/seguras, backups y restauración, logs y métricas.
8. Verificar diseño con WCAG 2.2 AA, teclado, lectores, móvil y localización.

Referencias normativas: OWASP ASVS 5.0, OWASP API Security Top 10 (2023), NIST SP 800-63B-4 y WCAG 2.2.

## Patrones visuales a aprobar ANTES de multiplicar pantallas

**Pantalla patrón A: Centro de control.** Cabecera con entorno, búsqueda global y actividad; navegación principal a izquierda, KPIs arriba, tabla con filtros y estados, panel de detalle contextual con acciones y motivo obligatorio. Jerarquía visual sobria, contraste AA, sin acciones críticas de un solo clic. Diseño escritorio/tablet/móvil responsive.

**Pantalla patrón B: Empresa — Campañas.** Cabecera y selector de empresa autorizado; KPIs de campañas; listado filtrable con estados/etapas; editor de campaña en vista de detalle con presupuesto, objetivo, agenda, equipo y historial; acción destacada 'Crear campaña' con validación y borrador automático. Mismo sistema de diseño pero espacio de trabajo distinto y sin navegación administrativa central.

**Puerta de aprobación:** el usuario revisa los dos patrones y pide ajustes. No replicar el diseño a otros tipos de pantalla antes de incorporar esos cambios.

## Gates

Diseño aprobado; threat model/roles aprobado; migraciones + tests unitarios/integración + E2E de dos tenants; permisos negativos; accesibilidad; pruebas carga; despliegue a staging; revisión de Google/SMS reales; rollback ensayado; checklist de lanzamiento firmado. Sin pruebas ejecutadas, NO GO.
