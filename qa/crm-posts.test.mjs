import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApi } from '../web/app/crm/crm-core.js';
import { existsSync, readFileSync } from 'node:fs';
import { workspaceView, frameWheelZoom } from '../web/app/crm/crm-ui.js';

const response = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });

test('visible post search and removal use the CRM routes', async () => {
  const seen = [];
  const api = createApi(async (path, request) => {
    seen.push([path, JSON.parse(request.body)]);
    const data = path.endsWith('/search')
      ? { posts: [], nextCursor: null }
      : { removed: true };
    return response({ success: true, data });
  });
  await api('platform/posts/search', 'jwt', { query: 'plaza' });
  await api('platform/posts/detail', 'jwt', { postId: '11111111-1111-4111-8111-111111111111' });
  await api('platform/posts/remove', 'jwt', { postIds: ['11111111-1111-4111-8111-111111111111'], reason: 'Texto ajeno' });
  await api('platform/posts/comments/remove', 'jwt', { commentId: '22222222-2222-4222-8222-222222222222', reason: 'Texto ajeno' });
  await api('platform/posts/anecdotes/remove', 'jwt', { anecdoteId: '33333333-3333-4333-8333-333333333333', reason: 'Texto ajeno' });
  await api('platform/users/search', 'jwt', { query: 'ana' });
  await api('platform/posts/reassign', 'jwt', { postId: '11111111-1111-4111-8111-111111111111', userId: '44444444-4444-4444-8444-444444444444' });
  assert.deepEqual(seen, [
    ['/api/v1/crm/platform/posts/search', { query: 'plaza' }],
    ['/api/v1/crm/platform/posts/detail', { postId: '11111111-1111-4111-8111-111111111111' }],
    ['/api/v1/crm/platform/posts/remove', { postIds: ['11111111-1111-4111-8111-111111111111'], reason: 'Texto ajeno' }],
    ['/api/v1/crm/platform/posts/comments/remove', { commentId: '22222222-2222-4222-8222-222222222222', reason: 'Texto ajeno' }],
    ['/api/v1/crm/platform/posts/anecdotes/remove', { anecdoteId: '33333333-3333-4333-8333-333333333333', reason: 'Texto ajeno' }],
    ['/api/v1/crm/platform/users/search', { query: 'ana' }],
    ['/api/v1/crm/platform/posts/reassign', { postId: '11111111-1111-4111-8111-111111111111', userId: '44444444-4444-4444-8444-444444444444' }],
  ]);
});

test('publicaciones abre el listado y no el tablero de resumen', () => {
  const publicaciones = { id: 'publicaciones', label: 'Publicaciones', view: 'listing' };
  const resumen = { id: 'resumen', label: 'Resumen', keys: null };
  assert.equal(workspaceView(publicaciones, {}), 'listing');
  assert.equal(workspaceView(resumen, {}), 'metrics');
  assert.equal(workspaceView({ id: 'usuarios', keys: ['Usuarios activos'] }, {}), 'metrics');
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const html = readFileSync(new URL('../web/app/crm/control/index.html', import.meta.url), 'utf8');
  assert.match(runtime, /id: 'publicaciones', label: 'Publicaciones', icon: 'qs_anecdote\.svg', view: 'listing'/);
  assert.doesNotMatch(runtime, /id: 'publicaciones'[^}]*keys:/);
  assert.match(runtime, /crm-ui\.js\?v=crop-1/);
  assert.match(html, /crm\.js\?v=crop-1/);
  assert.match(runtime, /publicationImage\(post\.imageUrl\)/);
  assert.match(runtime, /publicationImage\(post\.editorUrl\)/);
  assert.match(runtime, /imageUrl: saved\.imageUrl/);
  assert.match(html, /crm\.css\?v=crop-1/);
  assert.match(runtime, /icon: 'qs_trash\.png', label: 'Quitar selección'/);
  assert.match(runtime, /platform\/posts\/reassign/);
  assert.match(runtime, /platform\/users\/search/);
  assert.match(runtime, /authorship_reassign\.png', label: 'Cambiar autoría'/);
  assert.match(html, /data-crm-view="pending"/);
  assert.doesNotMatch(runtime, /Verificando identidad/);
  assert.match(runtime, /platform\/posts\/comments\/remove/);
  assert.match(runtime, /platform\/posts\/anecdotes\/remove/);
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  const css = readFileSync(new URL('../web/app/crm/crm.css', import.meta.url), 'utf8');
  const photo = css.match(/\.crm-post-photo \{[^}]+\}/);
  assert.ok(photo);
  assert.match(photo[0], /width: 120px/);
  assert.match(photo[0], /height: 72px/);
  assert.match(photo[0], /object-fit: cover/);
  assert.match(photo[0], /object-position: center center/);
  const ficha = css.match(/\.crm-dialog-photo \{[^}]+\}/);
  assert.ok(ficha);
  assert.match(ficha[0], /width: calc\(100% \+ 40px\)/);
  assert.match(ficha[0], /max-width: none/);
  assert.match(ficha[0], /height: auto/);
  assert.match(ficha[0], /object-fit: contain/);
  assert.doesNotMatch(ficha[0], /max-height/);
  assert.match(ui, /className = 'crm-actions'/);
  const actions = css.match(/\.crm-actions \{[^}]+\}/);
  assert.ok(actions);
  assert.match(actions[0], /display: inline-flex/);
  assert.match(actions[0], /flex-wrap: nowrap/);
  assert.match(css, /td:has\(\.crm-actions\)/);
  assert.match(css, /white-space: nowrap/);
  assert.match(css, /\.crm-actions \.crm-icon-btn \{ flex: none; \}/);
  assert.match(css, /\.crm-panel \{[^}]*min-width: 0/s);
  assert.match(css, /\.crm-panel \{[^}]*max-width: 100%/s);
  assert.match(css, /\.crm-table-wrap \{[^}]*overflow-x: auto/s);
  assert.match(css, /\.crm-table-wrap \{[^}]*overscroll-behavior-x: contain/s);
  assert.match(ui, /crm-photo-btn/);
  assert.match(ui, /Quitar comentario/);
  assert.match(ui, /Quitar anécdota/);
  assert.match(ui, /qs_trash\.png/);
  assert.match(ui, /qs_close\.svg', 'Cerrar'/);
  assert.match(ui, /Guardar encuadre/);
  assert.match(ui, /pointerdown/);
  assert.match(ui, /placeInFrame/);
  assert.match(runtime, /platform\/posts\/frame/);
  const core = readFileSync(new URL('../web/app/crm/crm-core.js', import.meta.url), 'utf8');
  assert.match(core, /platform\/posts\/frame/);
  assert.match(css, /\.crm-frame \{[^}]*aspect-ratio: 16 \/ 9/s);
  assert.match(css, /\.crm-frame \{[^}]*overflow: hidden/s);
  assert.match(ui, /crm-frame-wrap/);
  assert.match(css, /\.crm-frame-save \{[^}]*white-space: nowrap/s);
  assert.match(css, /\.crm-frame-save img \{[^}]*width: 20px/s);
  assert.doesNotMatch(css, /\.crm-frame-save \{[^}]*position: absolute/s);
  const webCss = readFileSync(new URL('../web/app/styles.css', import.meta.url), 'utf8');
  assert.match(webCss, /aspect-ratio: 16 \/ 9/);
  const app = readFileSync(new URL('../web/app/app.js', import.meta.url), 'utf8');
  assert.match(app, /function placeLiveFrame/);
  assert.match(ui, /Cambiar autoría/);
  assert.match(ui, /authorship_reassign\.png/);
  assert.doesNotMatch(ui, /function attributeControl/);
  assert.match(ui, /iconButton\(action\.icon, action\.label\)/);
  assert.doesNotMatch(ui, /marcada/);
  assert.doesNotMatch(ui, /tr\.addEventListener\('click', \(\) => open/);
  assert.match(css, /data-crm-view="pending"/);
  assert.match(runtime, /limitada a 12 horas/);
  assert.match(runtime, /async function returnToSignIn/);
  assert.doesNotMatch(runtime, /limitada a 15 minutos/);
  assert.match(ui, /search\.addEventListener\('input'/);
  assert.match(ui, /setTimeout\(\(\) => \{ void loadListing\(listing, true\); \}, 280\)/);
  assert.doesNotMatch(ui, /search\.addEventListener\('keydown'/);
});

test('la rueda del CRM web acerca arriba y aleja abajo', () => {
  assert.ok(frameWheelZoom(1, -100) > 1);
  assert.equal(frameWheelZoom(1, 120), 1);
  const closer = frameWheelZoom(1, -100);
  assert.ok(frameWheelZoom(closer, 100) < closer);
  assert.equal(frameWheelZoom(6, -300), 6);
  const ui = readFileSync(new URL('../web/app/crm/crm-ui.js', import.meta.url), 'utf8');
  assert.doesNotMatch(ui, /qs_zoom_in/);
  assert.doesNotMatch(ui, /qs_zoom_out/);
  assert.doesNotMatch(ui, /crm-frame-zoom/);
  assert.match(ui, /preventDefault\(\)/);
  assert.match(ui, /stopPropagation\(\)/);
  assert.match(ui, /capture: true, passive: false/);
  const css = readFileSync(new URL('../web/app/crm/crm.css', import.meta.url), 'utf8');
  assert.match(css, /\.crm-frame \{[^}]*overscroll-behavior: none/s);
  const empresas = readFileSync(new URL('../web/app/crm/empresas/index.html', import.meta.url), 'utf8');
  const control = readFileSync(new URL('../web/app/crm/control/index.html', import.meta.url), 'utf8');
  assert.match(empresas, /crm\.js\?v=crop-1/);
  assert.match(control, /crm\.js\?v=crop-1/);
  assert.doesNotMatch(css, /crm-frame-zoom/);
  assert.ok(existsSync(new URL('../web/app/crm/icons/qs_zoom_in.png', import.meta.url)));
  assert.ok(existsSync(new URL('../web/app/crm/icons/qs_zoom_out.png', import.meta.url)));
  const runtime = readFileSync(new URL('../web/app/crm/crm-runtime.js', import.meta.url), 'utf8');
  const empresaBlock = runtime.match(/const empresaSections = \[[\s\S]*?\];/);
  assert.ok(empresaBlock);
  assert.doesNotMatch(empresaBlock[0], /view: 'listing'/);
});
