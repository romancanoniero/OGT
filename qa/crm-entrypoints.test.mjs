import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const root = new URL('../', import.meta.url);
const html = readFileSync(new URL('web/app/index.html', root), 'utf8');
const bootstrap = readFileSync(new URL('web/app/bootstrap.js', root), 'utf8');
const nginx = readFileSync(new URL('web/app/nginx.conf', root), 'utf8');

test('public startup source has valid JavaScript syntax', () => {
  assert.doesNotThrow(() => new vm.Script(bootstrap));
});

test('public index asset references are not relative to a missing CRM route', () => {
  const attrs = [...html.matchAll(/(?:src|href)="([^"]+)"/g)].map(x => x[1]);
  const assets = attrs.filter(x => !x.startsWith('#') && !x.startsWith('https://'));
  assert.ok(assets.length >= 5);
  assert.ok(assets.every(x => x.startsWith('/')), JSON.stringify(assets));
  assert.match(html, /src="\/bootstrap\.js\?/);
  assert.doesNotMatch(html, /innerHTML\s*=\s*svg/);
});

for (const pathname of ['/crm', '/crm/control', '/crm/control/', '/crm/empresas/']) {
  test(`public fallback cannot initialize an application at ${pathname}`, () => {
    const shell = { hidden: false };
    const inserted = [];
    const context = {
      location: { pathname },
      document: {
        getElementById: id => id === 'app-shell' ? shell : null,
        createElement: () => ({ setAttribute() {}, textContent: '' }),
        body: { prepend: node => inserted.push(node) },
      },
      fetch() { assert.fail('No network requests expected from a mismatched CRM entrypoint'); },
    };
    vm.runInNewContext(bootstrap, context);
    assert.equal(shell.hidden, true);
    assert.equal(inserted.length, 1);
    assert.match(inserted[0].textContent, /servidor.*CRM/);
    assert.doesNotMatch(inserted[0].textContent, /404 Not Found/);
  });
}

test('CRM namespace has no public-index fallback', () => {
  const block = nginx.match(/location \^~ \/crm\/\s*\{([^}]+)\}/)?.[1];
  assert.ok(block);
  assert.match(block, /try_files \$uri \$uri\/ =404/);
  assert.doesNotMatch(block, /\/index\.html/);
  assert.match(block, /no-store/);
});

test('missing JavaScript and styles are real HTTP 404s', () => {
  assert.match(nginx, /location ~\*.*css\|js\|mjs[^\n]*\{\s*try_files \$uri =404/);
});

test('canonical CRM routes are not sent through the public SPA', () => {
  for (const name of ['control', 'empresas']) {
    assert.ok(nginx.includes(`location = /crm/${name} { return 308 /crm/${name}/; }`));
  }
});
