import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const read = path => readFileSync(new URL(path, root), 'utf8');

test('la identidad visual compacta mantiene el nombre oficial sin repetirlo junto a cada logo', () => {
  const index = read('web/app/index.html');
  const css = read('web/app/styles.css');
  const app = read('web/app/app.js');
  const crm = read('web/app/crm/crm-ui.js');
  const control = read('web/app/crm/control/index.html');
  const empresas = read('web/app/crm/empresas/index.html');
  const chrome = read('composeApp/src/commonMain/kotlin/com/onlygoodthings/app/ui/components/OgtChrome.kt');

  assert.match(index, /<title>Only Good Things \(OGT\)<\/title>/);
  assert.match(index, /ogt-mark--nav[^>]*alt="Inicio"/);
  assert.doesNotMatch(index, /<span class="brand-name">/);
  assert.match(css, /\.brand-name\s*\{\s*display:\s*none;/);

  const authBar = app.slice(app.indexOf('function authBar('), app.indexOf('function authFoot('));
  assert.match(authBar, /ogtMark\("bar"\)/);
  assert.doesNotMatch(authBar, /Only Good Things \(OGT\)/);
  const portal = app.slice(app.indexOf('function renderPortal('), app.indexOf('function renderAuth('));
  assert.doesNotMatch(portal, /auth-kicker/);

  assert.match(crm, /brandBox\.append\(productIcon\('ogt-logo\.png'\)\)/);
  assert.doesNotMatch(crm, /brandText\.append/);
  assert.match(control, /<h1>Administración central<\/h1>/);
  assert.match(empresas, /<h1>Empresas<\/h1>/);
  assert.match(chrome, /internal fun isAppWordmark\(title: String\)/);
});
