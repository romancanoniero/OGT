import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const root = new URL('../', import.meta.url);
const app = readFileSync(new URL('web/app/app.js', root), 'utf8');
const bootstrap = readFileSync(new URL('web/app/bootstrap.js', root), 'utf8');
const html = readFileSync(new URL('web/app/index.html', root), 'utf8');
const nginx = readFileSync(new URL('web/app/nginx.conf', root), 'utf8');
const crm = readFileSync(new URL('web/app/crm/crm-ui.js', root), 'utf8');

test('el aviso usa url y mediaUrls, los nombres que ya pinta la web', () => {
  assert.match(app, /function livePhotoUrl\(live\)/);
  assert.match(app, /live\?\.url/);
  assert.match(app, /live\?\.mediaUrls/);
  assert.match(app, /live\?\.imageUrl/);
  const photo = app.slice(app.indexOf('function livePhotoUrl'), app.indexOf('function replaceShownPhoto'));
  assert.ok(photo.indexOf('live?.url') < photo.indexOf('live?.mediaUrls'));
  assert.ok(photo.indexOf('live?.mediaUrls') < photo.indexOf('live?.imageUrl'));
  assert.match(app, /function replaceShownPhoto\(postId, imageUrl\)/);
  assert.match(app, /\[data-post="\$\{cssAttr\(postId\)\}"\] img\.media/);
  assert.match(app, /:scope > \.media-slot img\.media, :scope > img\.media/);
  assert.match(app, /function ensureLiveSlot\(img\)/);
  assert.match(app, /className = "media-slot"/);
  assert.match(app, /const shown = photo \? `<div class="media-slot">\$\{img\}<\/div>` : ""/);
  const css = readFileSync(new URL('web/app/styles.css', root), 'utf8');
  assert.match(css, /\.post \.media-slot/);
  assert.match(css, /aspect-ratio: 16 \/ 9/);
});

test('el feed publico no reutiliza el JavaScript del encuadre anterior', () => {
  assert.match(bootstrap, /\/ogt-sdk\.js\?v=frame-size/);
  assert.match(bootstrap, /\/app\.js\?v=frame-size/);
  assert.match(html, /\/bootstrap\.js\?v=frame-size/);
  assert.match(nginx, /location = \/index\.html[\s\S]*no-store/);
  assert.match(nginx, /location ~\*\s*\\\.\(css\|js\|mjs[\s\S]*?no-store/);
});

test('Control lee la misma foto sin cambiar el cromo', () => {
  assert.match(crm, /live\.url/);
  assert.match(crm, /live\.mediaUrls/);
  assert.match(crm, /live\.imageUrl/);
  assert.doesNotMatch(crm, /qs-zoom|Acercar|Alejar/);
});
