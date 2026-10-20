import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test } from 'node:test';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const appPath = path.join(root, 'web/app/app.js');
const src = readFileSync(appPath, 'utf8');

function extractFunction(name) {
  const start = src.indexOf(`function ${name}(`);
  assert.notEqual(start, -1, `falta function ${name}`);
  const open = src.indexOf('{', start);
  let depth = 0;
  for (let i = open; i < src.length; i += 1) {
    if (src[i] === '{') depth += 1;
    else if (src[i] === '}') {
      depth -= 1;
      if (depth === 0) return src.slice(start, i + 1);
    }
  }
  throw new Error(`función sin cerrar: ${name}`);
}

const sandbox = { location: { origin: 'http://localhost:19082' }, URL };
vm.createContext(sandbox);
vm.runInContext(
  `${extractFunction('escapeHtml')}\n${extractFunction('mediaUrl')}\n${extractFunction('httpSource')}\n` +
    'this.escapeHtml = escapeHtml; this.mediaUrl = mediaUrl; this.httpSource = httpSource;',
  sandbox,
);

test('escapeHtml neutraliza comillas y caracteres especiales', () => {
  const raw = `a<b>&"' \`/\u0000`;
  const safe = sandbox.escapeHtml(raw);
  assert.equal(safe.includes('<'), false);
  assert.equal(safe.includes('>'), false);
  assert.equal(safe.includes('"'), false);
  assert.equal(safe.includes("'"), false);
  assert.match(safe, /&lt;b&gt;/);
  assert.match(safe, /&amp;/);
  assert.match(safe, /&quot;/);
  assert.match(safe, /&#39;/);
  const html = `<p class="body">${safe}</p>`;
  assert.equal(html.includes('<b>'), false);
  assert.equal(html.includes('onerror='), false);
});

test('mediaUrl rechaza esquemas peligrosos y cortes de atributo', () => {
  for (const raw of [
    'javascript:alert(1)',
    'JaVaScRiPt:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'vbscript:msgbox(1)',
    ' javascript:alert(1)',
    '//evil.example/x',
    'x" onerror="alert(1)',
    'https://evil.example/a.jpg" onerror="alert(1)',
  ]) {
    assert.equal(sandbox.mediaUrl(raw), '', raw);
  }
  const external = sandbox.mediaUrl('https://ejemplo.test/nota.jpg');
  assert.equal(external, 'https://ejemplo.test/nota.jpg');
  const html = `<img src="${sandbox.escapeHtml(sandbox.mediaUrl('https://ejemplo.test/a.jpg?x=1&y=2'))}">`;
  assert.match(html, /src="https:\/\/ejemplo.test\/a.jpg\?x=1&amp;y=2"/);
});

test('mediaUrl conserva una ruta pública de medios y httpSource rechaza lo demás', () => {
  assert.equal(
    sandbox.mediaUrl('http://localhost:19082/media/u/foto.jpg?v=1'),
    '/media/u/foto.jpg?v=1',
  );
  assert.equal(sandbox.httpSource('https://ejemplo.test/nota'), true);
  assert.equal(sandbox.httpSource('javascript:alert(1)'), false);
  assert.equal(sandbox.httpSource('data:text/html,hola'), false);
  assert.equal(sandbox.httpSource('/media/u/foto.jpg'), false);
});

test('el enlace de avisos escapa el href generado', () => {
  assert.match(src, /href="\$\{escapeHtml\(hrefOf\(item\)\)\}"/);
  const hostile = '"><img src=x onerror=alert(1)>';
  const href = `#/mensajes/${hostile}`;
  const html = `<a href="${sandbox.escapeHtml(href)}">aviso</a>`;
  assert.equal(html.includes('href=""'), false);
  assert.match(html, /href="#\/mensajes\/&quot;&gt;&lt;img src=x onerror=alert\(1\)&gt;"/);
});
