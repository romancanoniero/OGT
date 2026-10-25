import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const load = name => readFileSync(new URL(name, root), 'utf8');
const runtime = load('web/app/crm/crm-runtime.js');
const core = load('web/app/crm/crm-core.js');
const crmUI = load('web/app/crm/crm-ui.js');
const publicApp = load('web/app/app.js');

function sectionSource(name) {
  const marker = `const ${name} = [`;
  const start = runtime.indexOf(marker);
  assert.notEqual(start, -1, `Missing ${name}`);
  const end = runtime.indexOf('];', start);
  assert.ok(end > start);
  return runtime.slice(start, end);
}

test('OGT Control declares all five visible workspaces', () => {
  const source = sectionSource('controlSections');
  for (const [id, label] of [
    ['resumen', 'Resumen'],
    ['usuarios', 'Usuarios'],
    ['empresas', 'Empresas'],
    ['denuncias', 'Denuncias'],
    ['publicaciones', 'Publicaciones'],
  ]) {
    assert.match(source, new RegExp(`id: '${id}', label: '${label}'`));
  }
  assert.match(source, /id: 'publicaciones'[^\n]+view: 'listing'/);
});

test('OGT Empresas declares all four workspaces', () => {
  const source = sectionSource('empresaSections');
  for (const [id, label] of [
    ['resumen', 'Resumen'],
    ['campanas', 'Campañas'],
    ['cupones', 'Cupones'],
    ['impacto', 'Impacto'],
  ]) {
    assert.match(source, new RegExp(`id: '${id}', label: '${label}'`));
  }
});

test('CRM permission, recent second factor and tenant checks cannot be bypassed by navigation', () => {
  assert.match(runtime, /validateAccess\(await api\('access'/);
  assert.match(runtime, /requireCompany\(permission, chosen\)/);
  assert.match(runtime, /TENANT_MISMATCH/);
  assert.match(core, /stepUpValidUntilEpochSeconds/);
  assert.match(core, /FORBIDDEN/);
  assert.match(runtime, /mode === 'control' \? controlSections : empresaSections/);
  assert.match(crmUI, /nav\.append\(button\)/);
});

test('web source retains each entrypoint used by the QA acceptance matrix', () => {
  for (const name of [
    'renderInicio', 'renderBuscar', 'renderMascotas', 'renderAyuda',
    'renderMensajes', 'renderAvisos', 'renderBilletera',
    'renderPerfil', 'renderAjustes', 'renderPublicar',
    'renderPortal', 'renderAuth', 'renderMfa',
    'renderEmpresaPanel', 'renderEmpresaCampanas', 'renderEmpresaComunidad',
  ]) {
    assert.match(publicApp, new RegExp(`(?:async )?function ${name}\\(`));
  }
});
